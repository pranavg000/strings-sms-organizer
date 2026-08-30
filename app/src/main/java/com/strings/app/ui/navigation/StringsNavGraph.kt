@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.strings.app.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.strings.app.ui.common.LocalNavAnimatedVisibilityScope
import com.strings.app.ui.common.LocalSharedTransitionScope
import com.strings.app.ui.detail.MessageDetailScreen
import com.strings.app.ui.filters.FilterEditScreen
import com.strings.app.ui.filters.FilterListScreen
import com.strings.app.ui.filters.FilterMessagesScreen
import com.strings.app.ui.finance.AccountDetailScreen
import com.strings.app.ui.finance.AccountEditScreen
import com.strings.app.ui.finance.FinanceDashboardScreen
import com.strings.app.ui.finance.ManageAccountsScreen
import com.strings.app.ui.help.HelpScreen
import com.strings.app.ui.inbox.AllMessagesScreen
import com.strings.app.ui.inbox.ArchivedMessagesScreen
import com.strings.app.ui.inbox.InboxScreen
import com.strings.app.ui.inbox.TagMessagesScreen
import com.strings.app.ui.inbox.TrashedMessagesScreen
import com.strings.app.ui.search.SearchScreen
import com.strings.app.ui.settings.SettingsScreen
import com.strings.app.ui.tags.TagEditScreen
import com.strings.app.ui.tags.TagListScreen
import com.strings.app.ui.theme.NavTransitions

/**
 * Motion pattern per destination type (M3 motion):
 * - Container transform: message detail — the tapped card morphs into the
 *   screen (shared bounds), so the screens themselves only crossfade.
 * - Fade-through: peer areas reached from the drawer/header — neither screen
 *   is "deeper" than the other.
 * - Shared axis Y: editors — "begin a task" screens that slide up slightly.
 * - Shared axis X (default): hierarchical drill-downs (list -> detail).
 * Forward transitions key off the destination being opened (targetState);
 * pops key off the destination being dismissed (initialState) so both sides
 * of a navigation always animate with the same pattern.
 */
private fun NavDestination.usesContainerTransform(): Boolean =
    hasRoute<MessageDetailRoute>() || hasRoute<AccountDetailRoute>()

private fun NavDestination.usesFadeThrough(): Boolean =
    hasRoute<SettingsRoute>() ||
        hasRoute<HelpRoute>() ||
        hasRoute<FinanceDashboardRoute>() ||
        hasRoute<SearchRoute>() ||
        hasRoute<FilterListRoute>() ||
        hasRoute<TagListRoute>() ||
        hasRoute<ManageAccountsRoute>()

private fun NavDestination.usesSharedAxisY(): Boolean =
    hasRoute<FilterEditRoute>() || hasRoute<TagEditRoute>() || hasRoute<AccountEditRoute>()

private fun enterFor(destination: NavDestination): EnterTransition = when {
    destination.usesContainerTransform() -> NavTransitions.containerTransformEnter()
    destination.usesFadeThrough() -> NavTransitions.fadeThroughEnter()
    destination.usesSharedAxisY() -> NavTransitions.sharedAxisYEnter()
    else -> NavTransitions.sharedAxisXEnter()
}

private fun exitFor(destination: NavDestination): ExitTransition = when {
    destination.usesContainerTransform() -> NavTransitions.containerTransformExit()
    destination.usesFadeThrough() -> NavTransitions.fadeThroughExit()
    destination.usesSharedAxisY() -> NavTransitions.sharedAxisYExit()
    else -> NavTransitions.sharedAxisXExit()
}

private fun popEnterFor(dismissed: NavDestination): EnterTransition = when {
    dismissed.usesContainerTransform() -> NavTransitions.containerTransformEnter()
    dismissed.usesFadeThrough() -> NavTransitions.fadeThroughEnter()
    dismissed.usesSharedAxisY() -> NavTransitions.sharedAxisYPopEnter()
    else -> NavTransitions.sharedAxisXPopEnter()
}

private fun popExitFor(dismissed: NavDestination): ExitTransition = when {
    dismissed.usesContainerTransform() -> NavTransitions.containerTransformExit()
    dismissed.usesFadeThrough() -> NavTransitions.fadeThroughExit()
    dismissed.usesSharedAxisY() -> NavTransitions.sharedAxisYPopExit()
    else -> NavTransitions.sharedAxisXPopExit()
}

/**
 * Exposes the destination's AnimatedVisibilityScope so shared elements
 * (message card <-> detail container transform) can animate with it.
 */
@Composable
private fun AnimatedContentScope.ProvideNavAnimationScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
        content()
    }
}

@Composable
fun StringsNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    deepLinkMessageId: Long? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    LaunchedEffect(deepLinkMessageId) {
        val messageId: Long = deepLinkMessageId ?: return@LaunchedEffect
        navController.navigate(MessageDetailRoute(messageId)) { launchSingleTop = true }
        onDeepLinkConsumed()
    }
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = InboxRoute,
                // The surface backdrop is what shows through during the fade dip of
                // shared-axis/fade-through transitions — without it the (light) window
                // background flashes white mid-transition in dark theme.
                modifier = modifier.background(MaterialTheme.colorScheme.surface),
                enterTransition = { enterFor(targetState.destination) },
                exitTransition = { exitFor(targetState.destination) },
                popEnterTransition = { popEnterFor(initialState.destination) },
                popExitTransition = { popExitFor(initialState.destination) }
            ) {
                composable<InboxRoute> {
                    ProvideNavAnimationScope {
                        InboxScreen(
                            onNavigateToSearch = { navController.navigate(SearchRoute) { launchSingleTop = true } },
                            onNavigateToFilters = { navController.navigate(FilterListRoute) { launchSingleTop = true } },
                            onNavigateToTags = { navController.navigate(TagListRoute) { launchSingleTop = true } },
                            onNavigateToDetail = { messageId ->
                                navController.navigate(MessageDetailRoute(messageId)) { launchSingleTop = true }
                            },
                            onNavigateToTagMessages = { tagId ->
                                navController.navigate(TagMessagesRoute(tagId)) { launchSingleTop = true }
                            },
                            onNavigateToFilterMessages = { filterId ->
                                navController.navigate(FilterMessagesRoute(filterId)) { launchSingleTop = true }
                            },
                            onNavigateToFilterEdit = {
                                navController.navigate(FilterEditRoute()) { launchSingleTop = true }
                            },
                            onNavigateToAllMessages = {
                                navController.navigate(AllMessagesRoute) { launchSingleTop = true }
                            },
                            onNavigateToArchivedMessages = {
                                navController.navigate(ArchivedMessagesRoute) { launchSingleTop = true }
                            },
                            onNavigateToTrashedMessages = {
                                navController.navigate(TrashedMessagesRoute) { launchSingleTop = true }
                            },
                            onNavigateToFinanceDashboard = {
                                navController.navigate(FinanceDashboardRoute) { launchSingleTop = true }
                            },
                            onNavigateToManageAccounts = {
                                navController.navigate(ManageAccountsRoute) { launchSingleTop = true }
                            },
                            onNavigateToSettings = {
                                navController.navigate(SettingsRoute) { launchSingleTop = true }
                            },
                            onNavigateToHelp = {
                                navController.navigate(HelpRoute) { launchSingleTop = true }
                            }
                        )
                    }
                }

                composable<AllMessagesRoute> {
                    ProvideNavAnimationScope {
                        AllMessagesScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToDetail = { messageId ->
                                navController.navigate(MessageDetailRoute(messageId)) { launchSingleTop = true }
                            },
                            onNavigateToFilterEdit = {
                                navController.navigate(FilterEditRoute()) { launchSingleTop = true }
                            }
                        )
                    }
                }
                composable<ArchivedMessagesRoute> {
                    ProvideNavAnimationScope {
                        ArchivedMessagesScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToDetail = { messageId ->
                                navController.navigate(MessageDetailRoute(messageId)) { launchSingleTop = true }
                            },
                            onNavigateToFilterEdit = {
                                navController.navigate(FilterEditRoute()) { launchSingleTop = true }
                            }
                        )
                    }
                }
                composable<TrashedMessagesRoute> {
                    ProvideNavAnimationScope {
                        TrashedMessagesScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToDetail = { messageId ->
                                navController.navigate(MessageDetailRoute(messageId)) { launchSingleTop = true }
                            },
                            onNavigateToFilterEdit = {
                                navController.navigate(FilterEditRoute()) { launchSingleTop = true }
                            }
                        )
                    }
                }
                composable<FinanceDashboardRoute> {
                    ProvideNavAnimationScope {
                        FinanceDashboardScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToMessage = { messageId ->
                                navController.navigate(MessageDetailRoute(messageId)) { launchSingleTop = true }
                            },
                            onNavigateToAccountDetail = { accountId ->
                                navController.navigate(AccountDetailRoute(accountId)) { launchSingleTop = true }
                            },
                            onNavigateToManageAccounts = {
                                navController.navigate(ManageAccountsRoute) { launchSingleTop = true }
                            }
                        )
                    }
                }

                composable<ManageAccountsRoute> {
                    ManageAccountsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToEdit = { accountId ->
                            navController.navigate(AccountEditRoute(accountId)) { launchSingleTop = true }
                        },
                        onNavigateToCreate = { bankCode, tail ->
                            navController.navigate(
                                AccountEditRoute(prefillBankCode = bankCode, prefillTail = tail)
                            ) { launchSingleTop = true }
                        }
                    )
                }
                composable<AccountEditRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<AccountEditRoute>()
                    AccountEditScreen(
                        accountId = route.accountId,
                        prefillBankCode = route.prefillBankCode,
                        prefillTail = route.prefillTail,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable<AccountDetailRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<AccountDetailRoute>()
                    ProvideNavAnimationScope {
                        AccountDetailScreen(
                            accountId = route.accountId,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToMessage = { messageId ->
                                navController.navigate(MessageDetailRoute(messageId)) { launchSingleTop = true }
                            }
                        )
                    }
                }
                composable<TagMessagesRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<TagMessagesRoute>()
                    ProvideNavAnimationScope {
                        TagMessagesScreen(
                            tagId = route.tagId,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToDetail = { messageId ->
                                navController.navigate(MessageDetailRoute(messageId)) { launchSingleTop = true }
                            },
                            onNavigateToFilterEdit = {
                                navController.navigate(FilterEditRoute()) { launchSingleTop = true }
                            }
                        )
                    }
                }
                composable<FilterMessagesRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<FilterMessagesRoute>()
                    ProvideNavAnimationScope {
                        FilterMessagesScreen(
                            filterId = route.filterId,
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToDetail = { messageId ->
                                navController.navigate(MessageDetailRoute(messageId)) { launchSingleTop = true }
                            },
                            onNavigateToFilterEdit = {
                                navController.navigate(FilterEditRoute()) { launchSingleTop = true }
                            }
                        )
                    }
                }
                composable<FilterListRoute> {
                    FilterListScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToEdit = { filterId ->
                            navController.navigate(FilterEditRoute(filterId)) { launchSingleTop = true }
                        },
                        onNavigateToCreate = {
                            navController.navigate(FilterEditRoute()) { launchSingleTop = true }
                        }
                    )
                }
                composable<FilterEditRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<FilterEditRoute>()
                    FilterEditScreen(
                        filterId = route.filterId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable<TagListRoute> {
                    TagListScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToEdit = { tagId ->
                            navController.navigate(TagEditRoute(tagId)) { launchSingleTop = true }
                        },
                        onNavigateToCreate = {
                            navController.navigate(TagEditRoute()) { launchSingleTop = true }
                        }
                    )
                }
                composable<TagEditRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<TagEditRoute>()
                    TagEditScreen(
                        tagId = route.tagId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable<MessageDetailRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<MessageDetailRoute>()
                    ProvideNavAnimationScope {
                        MessageDetailScreen(
                            messageId = route.messageId,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }

                composable<SettingsRoute> {
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable<HelpRoute> {
                    HelpScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable<SearchRoute> {
                    ProvideNavAnimationScope {
                        SearchScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToMessage = { messageId ->
                                navController.navigate(MessageDetailRoute(messageId)) { launchSingleTop = true }
                            },
                            onNavigateToFilterEdit = {
                                navController.navigate(FilterEditRoute()) { launchSingleTop = true }
                            }
                        )
                    }
                }
            }
        }
    }
}
