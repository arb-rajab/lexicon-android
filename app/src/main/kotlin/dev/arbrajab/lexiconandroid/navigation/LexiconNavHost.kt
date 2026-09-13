package dev.arbrajab.lexiconandroid.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.arbrajab.lexiconandroid.LexiconApplication
import dev.arbrajab.lexiconandroid.ui.corpora.CorpusListScreen
import dev.arbrajab.lexiconandroid.ui.corpora.CorpusListViewModel
import dev.arbrajab.lexiconandroid.ui.query.QueryScreen
import dev.arbrajab.lexiconandroid.ui.query.QueryViewModel
import dev.arbrajab.lexiconandroid.ui.serverconfig.ServerConfigScreen
import dev.arbrajab.lexiconandroid.ui.serverconfig.ServerConfigViewModel

private object Routes {
    const val SERVER_CONFIG = "server_config"
    const val CORPUS_LIST = "corpus_list"
    const val CORPUS_DETAIL = "corpus_detail/{corpusId}/{corpusName}"

    fun corpusDetail(corpusId: String, corpusName: String) = "corpus_detail/$corpusId/$corpusName"
}

@Composable
fun LexiconNavHost(app: LexiconApplication) {
    val navController = rememberNavController()
    val pendingCount by app.queryRepository.observePendingCount().collectAsState(initial = 0)

    NavHost(navController = navController, startDestination = Routes.SERVER_CONFIG) {
        composable(Routes.SERVER_CONFIG) {
            val viewModel: ServerConfigViewModel =
                viewModel(factory = ServerConfigViewModel.Factory(app.serverConfigStore))
            ServerConfigScreen(
                viewModel = viewModel,
                onContinue = { navController.navigate(Routes.CORPUS_LIST) }
            )
        }
        composable(Routes.CORPUS_LIST) {
            val viewModel: CorpusListViewModel =
                viewModel(
                    factory = CorpusListViewModel.Factory(
                        app.corpusRepository,
                        app.connectivityObserver
                    )
                )
            CorpusListScreen(
                viewModel = viewModel,
                pendingCount = pendingCount,
                onOpenCorpus = { id ->
                    val name =
                        viewModel.uiState.value.corpora.firstOrNull { it.id == id }?.name ?: ""
                    navController.navigate(Routes.corpusDetail(id, name))
                },
                onOpenSettings = { navController.navigate(Routes.SERVER_CONFIG) }
            )
        }
        composable(
            Routes.CORPUS_DETAIL,
            arguments =
            listOf(
                navArgument("corpusId") { type = NavType.StringType },
                navArgument("corpusName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val corpusId = backStackEntry.arguments?.getString("corpusId").orEmpty()
            val corpusName = backStackEntry.arguments?.getString("corpusName").orEmpty()
            val viewModel: QueryViewModel =
                viewModel(
                    factory =
                    QueryViewModel.Factory(
                        corpusId,
                        app.corpusRepository,
                        app.queryRepository,
                        app.connectivityObserver
                    )
                )
            QueryScreen(viewModel = viewModel, corpusName = corpusName, onBack = {
                navController.popBackStack()
            })
        }
    }
}
