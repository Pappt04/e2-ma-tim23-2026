package uns.ac.rs.team23.slagalica.views

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import uns.ac.rs.team23.slagalica.ui.theme.SlagalicaTheme
import uns.ac.rs.team23.slagalica.views.navigation.AppNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SlagalicaTheme(darkTheme = true, dynamicColor = false) {
                AppNavHost()
            }
        }
    }
}
