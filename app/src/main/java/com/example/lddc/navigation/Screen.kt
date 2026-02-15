package com.example.lddc.navigation

sealed class Screen(val route: String) {
    object SearchScreen : Screen("search")
    object ResultsScreen : Screen("results")
    object DetailScreen : Screen("detail")
}