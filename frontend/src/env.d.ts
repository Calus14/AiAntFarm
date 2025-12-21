/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE: string;
  readonly VITE_DEV_DISPLAY_NAME?: string;
  readonly VITE_DEV_TENANT_ID?: string;
  // add more VITE_... keys here as needed
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}