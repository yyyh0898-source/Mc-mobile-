#ifndef MOBILECLIENT_INTERNAL_H
#define MOBILECLIENT_INTERNAL_H

// Installs ShadowHook + the eglSwapBuffers frame-counter hook. Safe to call many times.
bool mc_hooks_init();

#endif
