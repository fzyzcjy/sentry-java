package io.sentry.samples.android.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.sentry.Sentry
import io.sentry.compose.withSentryObservableEffect
import io.sentry.samples.android.GithubAPI
import kotlinx.coroutines.launch

class ComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberNavController().withSentryObservableEffect()
            SampleNavigation(navController)
        }
    }

    override fun onResume() {
        super.onResume()
        Sentry.getSpan()?.finish()
    }
}

@Composable
fun Landing(
    navigateGithub: () -> Unit,
    navigateGithubWithArgs: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Button(
            onClick = {
                navigateGithub()
            },
            modifier = Modifier
                .testTag("button_nav_github")
                .padding(top = 32.dp)
        ) {
            Text("Navigate to Github Page")
        }
        Button(
            onClick = { navigateGithubWithArgs() },
            modifier = Modifier
                .testTag("button_nav_github_args")
                .padding(top = 32.dp)
        ) {
            Text("Navigate to Github Page With Args")
        }
        Button(
            onClick = { throw RuntimeException("Crash from Compose") },
            modifier = Modifier
                .testTag("button_crash")
                .padding(top = 32.dp)
        ) {
            Text("Crash from Compose")
        }
    }
}

@Composable
fun Github(
    user: String = "getsentry",
    perPage: Int = 30
) {
    var user by remember { mutableStateOf(TextFieldValue(user)) }
    var result by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(perPage) {
        result = GithubAPI.service.listReposAsync(user.text, perPage).random().full_name
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        TextField(
            value = user,
            onValueChange = { newText ->
                user = newText
            }
        )
        Text("Random repo $result")
        Button(
            onClick = {
                scope.launch {
                    result = GithubAPI.service.listReposAsync(user.text, perPage).random().full_name
                }
            },
            modifier = Modifier
                .testTag("button_list_repos_async")
                .padding(top = 32.dp)
        ) {
            Text("Make Request")
        }
    }
}

@Composable
fun SampleNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Destination.Landing.route
    ) {
        composable(Destination.Landing.route) {
            Landing(
                navigateGithub = { navController.navigate("github") },
                navigateGithubWithArgs = { navController.navigate("github/spotify?per_page=10") }
            )
        }
        composable(Destination.Github.route) {
            Github()
        }
        composable(
            Destination.GithubWithArgs.route,
            arguments = listOf(
                navArgument(Destination.USER_ARG) { type = NavType.StringType },
                navArgument(Destination.PER_PAGE_ARG) { type = NavType.IntType; defaultValue = 10 }
            )
        ) {
            Github(
                it.arguments?.getString(Destination.USER_ARG) ?: "getsentry",
                it.arguments?.getInt(Destination.PER_PAGE_ARG) ?: 10
            )
        }
    }
}

sealed class Destination(
    val route: String
) {
    object Landing : Destination("landing")
    object Github : Destination("github")
    object GithubWithArgs : Destination("github/{$USER_ARG}?$PER_PAGE_ARG={$PER_PAGE_ARG}")

    companion object {
        const val USER_ARG = "user"
        const val PER_PAGE_ARG = "per_page"
    }
}
