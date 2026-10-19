package app.pokeplan.ui

import app.pokeplan.app.cloud.CloudConfig

/**
 * Where the Supabase project is. Fill these in from Project Settings → API (the Project URL and the anon / publishable key).
 * Both are meant to be in the app, they are not secrets. While they are empty the app works offline only and Settings says
 * online features are not set up. See supabase/README.md.
 */
object CloudSettings {
    const val URL = ""
    const val ANON_KEY = ""

    val config = CloudConfig(URL, ANON_KEY)
}
