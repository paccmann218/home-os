/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Where "Download the app" points. Defaults to the GitHub release asset homeos-bridge.apk. */
  readonly VITE_APK_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
