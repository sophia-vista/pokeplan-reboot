package app.pokeplan.ui

import app.pokeplan.app.cloud.CloudConfig

/**
 * Where the Supabase project is. Fill these in from Project Settings → API (the Project URL and the anon / publishable key).
 * Both are meant to be in the app, they are not secrets. While they are empty the app works offline only and Settings says
 * online features are not set up. See supabase/README.md.
 */
object CloudSettings {
    const val URL = "https://pnqbeiicririgfavhqxm.supabase.co"
    const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InBucWJlaWljcmlyaWdmYXZocXhtIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTIzODAxMTEsImV4cCI6MjEwNzk1NjExMX0.J9bUARTLUjz1WXQnswXsJy-JGvQfCeAtKCIZEWooPrk"

    val config = CloudConfig(URL, ANON_KEY)
}
