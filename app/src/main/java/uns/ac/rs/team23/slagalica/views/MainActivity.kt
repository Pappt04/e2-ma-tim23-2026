package uns.ac.rs.team23.slagalica.views

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import uns.ac.rs.team23.slagalica.ui.theme.SlagalicaTheme
import uns.ac.rs.team23.slagalica.views.welcome.WelcomePage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SlagalicaTheme {
                WelcomePage()
            }
        }
    }
}
