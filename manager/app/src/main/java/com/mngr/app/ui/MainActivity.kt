package com.mngr.app.ui

/**
 * The launcher entry point the manifest names; the screen itself is inherited.
 *
 * Since the FoxSu UI port, the launcher opens [me.diksu.ultra.foxsu.ui.MainActivity]
 * (5 skins + dock effects). Our own shell is still in the tree under
 * `me.diksu.ultra.ui.MainActivity` — it owns the jailbreak and partition-guard
 * entry points that have not been moved into the new shell yet.
 */
class MainActivity : me.diksu.ultra.foxsu.ui.MainActivity()
