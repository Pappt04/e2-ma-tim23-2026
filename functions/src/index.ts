import * as admin from "firebase-admin";
import * as functions from "firebase-functions";

admin.initializeApp();

const db = admin.firestore();
const rtdb = admin.database();

// ─── Auth trigger ──────────────────────────────────────────────────────────────

export const onUserCreated = functions.auth.user().onCreate(async (user) => {
  const uid = user.uid;
  const userRef = db.collection("users").doc(uid);
  const existing = await userRef.get();
  if (existing.exists) return; // client already wrote it

  const now = admin.firestore.Timestamp.now();
  const today = new Date().toISOString().split("T")[0];

  await userRef.set({
    uid,
    username: user.displayName || `User_${uid.slice(0, 6)}`,
    email: user.email || "",
    region: "",
    tokens: 5,
    stars: 0,
    totalStarsEarned: 0,
    leagueLevel: 0,
    avatarIndex: 0,
    isGuest: user.providerData.length === 0,
    createdAt: now,
    lastTokenGranted: today,
    fcmToken: "",
    sessionActive: true,
  });
});

// ─── RTDB trigger: matchmaking ────────────────────────────────────────────────

export const onQueueEntryCreated = functions.database
  .ref("/queue/{uid}")
  .onCreate(async (snap, context) => {
    const joiningUid = context.params.uid;
    const queueRef = rtdb.ref("queue");

    // Try to atomically claim an opponent
    let opponentUid: string | null = null;

    await queueRef.transaction((currentQueue: Record<string, unknown> | null) => {
      if (!currentQueue) return currentQueue;

      const entries = Object.entries(currentQueue)
        .filter(([uid]) => uid !== joiningUid)
        .sort(([, a], [, b]) => {
          const aT = (a as Record<string, number>).joinedAt ?? 0;
          const bT = (b as Record<string, number>).joinedAt ?? 0;
          return aT - bT;
        });

      if (entries.length === 0) return currentQueue;

      opponentUid = entries[0][0];
      delete currentQueue[joiningUid];
      delete currentQueue[opponentUid];
      return currentQueue;
    });

    if (!opponentUid) return; // no opponent yet

    // Fetch both user profiles
    const [p1Doc, p2Doc] = await Promise.all([
      db.collection("users").doc(joiningUid).get(),
      db.collection("users").doc(opponentUid).get(),
    ]);

    const p1 = p1Doc.data()!;
    const p2 = p2Doc.data()!;
    const gameTypes = ["KO_ZNA_ZNA", "SPOJNICE", "ASOCIJACIJE", "SKOCKO", "KORAK_PO_KORAK", "MOJ_BROJ"];
    const now = admin.firestore.Timestamp.now();

    // Create Firestore match document
    const matchRef = db.collection("matches").doc();
    await matchRef.set({
      player1Id: joiningUid,
      player1Username: p1.username,
      player2Id: opponentUid,
      player2Username: p2.username,
      status: "IN_PROGRESS",
      isFriendly: false,
      player1TotalScore: 0,
      player2TotalScore: 0,
      currentGameIndex: 0,
      currentGameType: gameTypes[0],
      winnerId: null,
      createdAt: now,
      completedAt: null,
    });

    const matchId = matchRef.id;

    // Write RTDB state
    await Promise.all([
      rtdb.ref(`matchState/${matchId}`).set({
        status: "IN_PROGRESS",
        currentGameIndex: 0,
        currentGameType: gameTypes[0],
        player1Score: 0,
        player2Score: 0,
        lastUpdated: admin.database.ServerValue.TIMESTAMP,
      }),
      rtdb.ref(`userActiveMatch/${joiningUid}`).set(matchId),
      rtdb.ref(`userActiveMatch/${opponentUid}`).set(matchId),
    ]);

    // Notify both players via FCM
    const tokens = [p1.fcmToken, p2.fcmToken].filter(Boolean);
    if (tokens.length > 0) {
      await admin.messaging().sendEachForMulticast({
        tokens,
        data: {
          type: "MATCH_FOUND",
          matchId,
          title: "Protivnik pronađen!",
          body: "Partija počinje",
        },
      });
    }
  });

// ─── Firestore trigger: push FCM when in-app notification is written ───────────

export const onUserNotificationCreated = functions.firestore
  .document("users/{userId}/notifications/{notificationId}")
  .onCreate(async (snap, context) => {
    const userId = context.params.userId;
    const data = snap.data();
    const userDoc = await db.collection("users").doc(userId).get();
    const token = userDoc.data()?.fcmToken as string | undefined;
    if (!token) return;

    const type = (data.type as string) || "OTHER";
    const channelType = type.toUpperCase();
    await admin.messaging().send({
      token,
      data: {
        type: channelType,
        title: (data.title as string) || "Slagalica",
        body: (data.message as string) || "",
        notificationId: (data.id as string) || snap.id,
        inviteId: (data.inviteId as string) || "",
      },
    });
  });

// ─── Callable: submit match score ─────────────────────────────────────────────

export const submitMatchScore = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError("unauthenticated", "Not authenticated");

  const uid = context.auth.uid;
  const { matchId, score } = data as { matchId: string; score: number };

  const matchRef = db.collection("matches").doc(matchId);
  const matchDoc = await matchRef.get();
  if (!matchDoc.exists) throw new functions.https.HttpsError("not-found", "Match not found");

  const match = matchDoc.data()!;
  const isPlayer1 = match.player1Id === uid;
  const isPlayer2 = match.player2Id === uid;
  if (!isPlayer1 && !isPlayer2) throw new functions.https.HttpsError("permission-denied", "Not a participant");

  const gameType = match.currentGameType as string;
  const gameResultRef = matchRef.collection("gameResults").doc(gameType);

  await db.runTransaction(async (tx) => {
    const resultDoc = await tx.get(gameResultRef);
    const existing = resultDoc.exists ? resultDoc.data()! : {
      gameType,
      gameIndex: match.currentGameIndex,
      player1Score: 0,
      player2Score: 0,
      player1Completed: false,
      player2Completed: false,
      createdAt: admin.firestore.Timestamp.now(),
      completedAt: null,
    };

    if (isPlayer1) {
      existing.player1Score = score;
      existing.player1Completed = true;
    } else {
      existing.player2Score = score;
      existing.player2Completed = true;
    }

    tx.set(gameResultRef, existing);

    // If both completed, advance the game
    if (existing.player1Completed && existing.player2Completed) {
      existing.completedAt = admin.firestore.Timestamp.now();
      tx.set(gameResultRef, existing);

      const nextIndex = (match.currentGameIndex as number) + 1;
      const gameTypes = ["KO_ZNA_ZNA", "SPOJNICE", "ASOCIJACIJE", "SKOCKO", "KORAK_PO_KORAK", "MOJ_BROJ"];

      const p1Total = (match.player1TotalScore as number) + existing.player1Score;
      const p2Total = (match.player2TotalScore as number) + existing.player2Score;

      if (nextIndex >= gameTypes.length) {
        // Match over
        const winnerId = p1Total > p2Total
          ? match.player1Id
          : p2Total > p1Total ? match.player2Id : null;

        tx.update(matchRef, {
          status: "COMPLETED",
          currentGameIndex: nextIndex,
          currentGameType: null,
          player1TotalScore: p1Total,
          player2TotalScore: p2Total,
          winnerId,
          completedAt: admin.firestore.Timestamp.now(),
        });

        // Award stars (async, outside transaction is fine for the university project)
        awardStars(match.player1Id, match.player2Id, p1Total, p2Total).catch(console.error);
      } else {
        tx.update(matchRef, {
          currentGameIndex: nextIndex,
          currentGameType: gameTypes[nextIndex],
          player1TotalScore: p1Total,
          player2TotalScore: p2Total,
        });
      }
    }
  });

  const updated = await matchRef.get();
  return { ...updated.data(), id: matchId };
});

async function awardStars(
  p1Id: string,
  p2Id: string,
  p1Score: number,
  p2Score: number,
): Promise<void> {
  const p1Won = p1Score > p2Score;

  const p1Stars = p1Won
    ? 10 + Math.floor(p1Score / 40)
    : -10 + Math.floor(p1Score / 40);
  const p2Stars = !p1Won
    ? 10 + Math.floor(p2Score / 40)
    : -10 + Math.floor(p2Score / 40);

  const [p1Doc, p2Doc] = await Promise.all([
    db.collection("users").doc(p1Id).get(),
    db.collection("users").doc(p2Id).get(),
  ]);

  const p1Data = p1Doc.data()!;
  const p2Data = p2Doc.data()!;

  const p1NewStars = Math.max(0, (p1Data.stars as number) + p1Stars);
  const p2NewStars = Math.max(0, (p2Data.stars as number) + p2Stars);

  const leagueThresholds = [0, 100, 200, 400, 800, 1600];
  const leagueFor = (stars: number) => {
    for (let i = leagueThresholds.length - 1; i >= 0; i--) {
      if (stars >= leagueThresholds[i]) return i;
    }
    return 0;
  };

  await Promise.all([
    db.collection("users").doc(p1Id).update({
      stars: p1NewStars,
      totalStarsEarned: admin.firestore.FieldValue.increment(Math.max(0, p1Stars)),
      leagueLevel: leagueFor(p1NewStars),
    }),
    db.collection("users").doc(p2Id).update({
      stars: p2NewStars,
      totalStarsEarned: admin.firestore.FieldValue.increment(Math.max(0, p2Stars)),
      leagueLevel: leagueFor(p2NewStars),
    }),
  ]);
}

// ─── Callable: abandon match ──────────────────────────────────────────────────

export const abandonMatch = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError("unauthenticated", "Not authenticated");

  const uid = context.auth.uid;
  const { matchId } = data as { matchId: string };

  const matchRef = db.collection("matches").doc(matchId);
  const matchDoc = await matchRef.get();
  if (!matchDoc.exists) throw new functions.https.HttpsError("not-found", "Match not found");

  const match = matchDoc.data()!;
  const isPlayer1 = match.player1Id === uid;
  const isPlayer2 = match.player2Id === uid;
  if (!isPlayer1 && !isPlayer2) throw new functions.https.HttpsError("permission-denied", "Not a participant");

  const winnerId = isPlayer1 ? match.player2Id : match.player1Id;

  await matchRef.update({
    status: "ABANDONED",
    winnerId,
    completedAt: admin.firestore.Timestamp.now(),
  });

  // Clear RTDB active match entries
  await Promise.all([
    rtdb.ref(`userActiveMatch/${match.player1Id}`).remove(),
    rtdb.ref(`userActiveMatch/${match.player2Id}`).remove(),
  ]);

  const updated = await matchRef.get();
  return { ...updated.data(), id: matchId };
});

// ─── Callable: send match invite ──────────────────────────────────────────────

export const sendMatchInvite = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError("unauthenticated", "Not authenticated");

  const uid = context.auth.uid;
  const { inviteeId, friendly } = data as { inviteeId: string; friendly: boolean };

  const inviterDoc = await db.collection("users").doc(uid).get();
  const inviteeDoc = await db.collection("users").doc(inviteeId).get();

  if (!inviteeDoc.exists) throw new functions.https.HttpsError("not-found", "User not found");

  const inviter = inviterDoc.data()!;
  const invitee = inviteeDoc.data()!;

  const now = admin.firestore.Timestamp.now();
  const expiresAt = admin.firestore.Timestamp.fromDate(new Date(Date.now() + 10_000));

  const inviteRef = await db.collection("matchInvites").add({
    inviterId: uid,
    inviterUsername: inviter.username,
    inviteeId,
    inviteeUsername: invitee.username,
    isFriendly: friendly,
    status: "PENDING",
    createdAt: now,
    expiresAt,
  });

  // Push to RTDB so the invitee's client sees it instantly
  await rtdb.ref(`invites/${inviteeId}/${inviteRef.id}`).set({
    inviterId: uid,
    inviterUsername: inviter.username,
    isFriendly: friendly,
    createdAt: Date.now(),
    expiresAt: Date.now() + 10_000,
  });

  // Send FCM if invitee has a token
  if (invitee.fcmToken) {
    await admin.messaging().send({
      token: invitee.fcmToken,
      data: {
        type: "INVITE",
        inviteId: inviteRef.id,
        title: "Match invite",
        body: `${inviter.username} te izaziva na ${friendly ? "prijateljsku" : "rankiranu"} partiju`,
        notificationId: `invite_${inviteRef.id}`,
      },
    });
  }

  return {
    id: inviteRef.id,
    player1Id: uid,
    player1Username: inviter.username,
    player2Id: inviteeId,
    player2Username: invitee.username,
    status: "INVITE_SENT",
    isFriendly: friendly,
    currentGameIndex: 0,
    currentGameType: null,
    player1TotalScore: 0,
    player2TotalScore: 0,
    winnerId: null,
  };
});

// ─── Callable: respond to invite ──────────────────────────────────────────────

export const respondToInvite = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError("unauthenticated", "Not authenticated");

  const uid = context.auth.uid;
  const { inviteId, accept } = data as { inviteId: string; accept: boolean };

  const inviteRef = db.collection("matchInvites").doc(inviteId);
  const inviteDoc = await inviteRef.get();
  if (!inviteDoc.exists) throw new functions.https.HttpsError("not-found", "Invite not found");

  const invite = inviteDoc.data()!;
  if (invite.inviteeId !== uid) throw new functions.https.HttpsError("permission-denied", "Not the invitee");

  await rtdb.ref(`invites/${uid}/${inviteId}`).remove();

  if (!accept) {
    await inviteRef.update({ status: "REJECTED" });
    return { id: inviteId, status: "REJECTED", player1Id: invite.inviterId, player1Username: invite.inviterUsername, player2Id: uid, player2Username: "", isFriendly: false, currentGameIndex: 0, currentGameType: null, player1TotalScore: 0, player2TotalScore: 0, winnerId: null };
  }

  await inviteRef.update({ status: "ACCEPTED" });

  const gameTypes = ["KO_ZNA_ZNA", "SPOJNICE", "ASOCIJACIJE", "SKOCKO", "KORAK_PO_KORAK", "MOJ_BROJ"];
  const now = admin.firestore.Timestamp.now();

  const [inviterDoc, inviteeDoc] = await Promise.all([
    db.collection("users").doc(invite.inviterId).get(),
    db.collection("users").doc(uid).get(),
  ]);

  const inviter = inviterDoc.data()!;
  const invitee = inviteeDoc.data()!;

  const matchRef = db.collection("matches").doc();
  await matchRef.set({
    player1Id: invite.inviterId,
    player1Username: inviter.username,
    player2Id: uid,
    player2Username: invitee.username,
    status: "IN_PROGRESS",
    isFriendly: invite.isFriendly,
    player1TotalScore: 0,
    player2TotalScore: 0,
    currentGameIndex: 0,
    currentGameType: gameTypes[0],
    winnerId: null,
    createdAt: now,
    completedAt: null,
  });

  const matchId = matchRef.id;

  await Promise.all([
    rtdb.ref(`matchState/${matchId}`).set({
      status: "IN_PROGRESS",
      currentGameIndex: 0,
      currentGameType: gameTypes[0],
      lastUpdated: admin.database.ServerValue.TIMESTAMP,
    }),
    rtdb.ref(`userActiveMatch/${invite.inviterId}`).set(matchId),
    rtdb.ref(`userActiveMatch/${uid}`).set(matchId),
  ]);

  return { id: matchId, player1Id: invite.inviterId, player1Username: inviter.username, player2Id: uid, player2Username: invitee.username, status: "IN_PROGRESS", isFriendly: invite.isFriendly, currentGameIndex: 0, currentGameType: gameTypes[0], player1TotalScore: 0, player2TotalScore: 0, winnerId: null };
});

// ─── Callable: cancel invite ──────────────────────────────────────────────────

export const cancelMatchInvite = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError("unauthenticated", "Not authenticated");

  const uid = context.auth.uid;
  const { inviteId } = data as { inviteId: string };

  const inviteDoc = await db.collection("matchInvites").doc(inviteId).get();
  if (!inviteDoc.exists) return;

  const invite = inviteDoc.data()!;
  if (invite.inviterId !== uid) throw new functions.https.HttpsError("permission-denied", "Not the inviter");

  await db.collection("matchInvites").doc(inviteId).update({ status: "EXPIRED" });
  await rtdb.ref(`invites/${invite.inviteeId}/${inviteId}`).remove();
});

// ─── Callable: join/create challenge ─────────────────────────────────────────

export const joinChallenge = functions.https.onCall(async (data, context) => {
  if (!context.auth) throw new functions.https.HttpsError("unauthenticated", "Not authenticated");

  const uid = context.auth.uid;
  const { challengeId, create, region, stakedStars, stakedTokens } = data as {
    challengeId?: string;
    create: boolean;
    region?: string;
    stakedStars?: number;
    stakedTokens?: number;
  };

  const userDoc = await db.collection("users").doc(uid).get();
  const user = userDoc.data()!;

  if (create) {
    // Create new challenge
    const stars = stakedStars ?? 0;
    const tokens = stakedTokens ?? 0;

    if ((user.stars as number) < stars) throw new functions.https.HttpsError("failed-precondition", "Nemate dovoljno zvezda");
    if ((user.tokens as number) < tokens) throw new functions.https.HttpsError("failed-precondition", "Nemate dovoljno tokena");

    const challengeRef = db.collection("challenges").doc();
    const now = admin.firestore.Timestamp.now();

    await db.runTransaction(async (tx) => {
      tx.set(challengeRef, {
        creatorId: uid,
        creatorUsername: user.username,
        region: region ?? user.region,
        stakedStars: stars,
        stakedTokens: tokens,
        status: "OPEN",
        participantIds: [uid],
        participantCount: 1,
        createdAt: now,
        completedAt: null,
      });

      tx.set(challengeRef.collection("participants").doc(uid), {
        uid,
        username: user.username,
        totalScore: 0,
        gamesCompleted: 0,
        joinedAt: now,
      });

      tx.update(db.collection("users").doc(uid), {
        stars: admin.firestore.FieldValue.increment(-stars),
        tokens: admin.firestore.FieldValue.increment(-tokens),
      });
    });

    const doc = await challengeRef.get();
    const participants = [{ id: uid, username: user.username, totalScore: 0, gamesCompleted: 0 }];
    return buildChallengeResponse(doc, participants);
  }

  // Join existing challenge
  const challengeRef = db.collection("challenges").doc(challengeId!);

  return db.runTransaction(async (tx) => {
    const challengeDoc = await tx.get(challengeRef);
    if (!challengeDoc.exists) throw new functions.https.HttpsError("not-found", "Challenge not found");

    const challenge = challengeDoc.data()!;
    if (challenge.status !== "OPEN") throw new functions.https.HttpsError("failed-precondition", "Challenge is not open");
    if ((challenge.participantCount as number) >= 4) throw new functions.https.HttpsError("failed-precondition", "Challenge is full");

    const stars = challenge.stakedStars as number;
    const tokens = challenge.stakedTokens as number;

    if ((user.stars as number) < stars) throw new functions.https.HttpsError("failed-precondition", "Nemate dovoljno zvezda");
    if ((user.tokens as number) < tokens) throw new functions.https.HttpsError("failed-precondition", "Nemate dovoljno tokena");

    const now = admin.firestore.Timestamp.now();

    tx.set(challengeRef.collection("participants").doc(uid), {
      uid,
      username: user.username,
      totalScore: 0,
      gamesCompleted: 0,
      joinedAt: now,
    });

    tx.update(challengeRef, {
      participantIds: admin.firestore.FieldValue.arrayUnion(uid),
      participantCount: admin.firestore.FieldValue.increment(1),
    });

    tx.update(db.collection("users").doc(uid), {
      stars: admin.firestore.FieldValue.increment(-stars),
      tokens: admin.firestore.FieldValue.increment(-tokens),
    });

    const participantsSnap = await challengeRef.collection("participants").get();
    const participants = participantsSnap.docs.map((p) => ({
      id: p.id,
      username: p.data().username as string,
      totalScore: p.data().totalScore as number,
      gamesCompleted: p.data().gamesCompleted as number,
    }));
    participants.push({ id: uid, username: user.username, totalScore: 0, gamesCompleted: 0 });

    return buildChallengeResponse(challengeDoc, participants);
  });
});

function buildChallengeResponse(
  doc: admin.firestore.DocumentSnapshot,
  participants: Array<{ id: string; username: string; totalScore: number; gamesCompleted: number }>,
) {
  const d = doc.data()!;
  return {
    id: doc.id,
    creatorUsername: d.creatorUsername,
    region: d.region,
    stakedStars: d.stakedStars,
    stakedTokens: d.stakedTokens,
    status: d.status,
    participants,
  };
}

// ─── Scheduled: daily token grant ────────────────────────────────────────────

export const dailyTokenGrant = functions.pubsub
  .schedule("every 24 hours")
  .onRun(async () => {
    const today = new Date().toISOString().split("T")[0];
    const snapshot = await db.collection("users")
      .where("lastTokenGranted", "<", today)
      .get();

    const batch = db.batch();
    snapshot.docs.forEach((doc) => {
      const leagueLevel = (doc.data().leagueLevel as number) ?? 0;
      batch.update(doc.ref, {
        tokens: admin.firestore.FieldValue.increment(5 + leagueLevel),
        lastTokenGranted: today,
      });
    });

    await batch.commit();
    console.log(`Granted daily tokens to ${snapshot.size} users`);
  });

// ─── Scheduled: rebuild leaderboards ─────────────────────────────────────────

export const rebuildLeaderboards = functions.pubsub
  .schedule("every 60 minutes")
  .onRun(async () => {
    const snapshot = await db.collection("users")
      .orderBy("stars", "desc")
      .limit(100)
      .get();

    const entries = snapshot.docs.map((doc) => ({
      uid: doc.id,
      username: doc.data().username,
      stars: doc.data().stars,
      leagueLevel: doc.data().leagueLevel,
      region: doc.data().region,
    }));

    const now = admin.firestore.Timestamp.now();
    await Promise.all([
      db.collection("leaderboards").doc("weekly").set({ generatedAt: now, entries }),
      db.collection("leaderboards").doc("monthly").set({ generatedAt: now, entries }),
    ]);

    console.log("Rebuilt leaderboards");
  });

// ─── Scheduled: prune old chat messages ──────────────────────────────────────

export const pruneOldChatMessages = functions.pubsub
  .schedule("every 60 minutes")
  .onRun(async () => {
    const regions = await db.collection("chat").listDocuments();
    for (const regionRef of regions) {
      const msgs = await regionRef.collection("messages")
        .orderBy("sentAt", "desc")
        .get();

      if (msgs.size <= 200) continue;

      const toDelete = msgs.docs.slice(200);
      const batch = db.batch();
      toDelete.forEach((doc) => batch.delete(doc.ref));
      await batch.commit();
    }
    console.log("Pruned old chat messages");
  });
