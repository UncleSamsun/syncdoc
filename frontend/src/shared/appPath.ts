/** Vite base is an absolute application path; routing and browser URLs share it. */
export const APP_BASE = import.meta.env.BASE_URL.replace(/\/$/, "");
export function appPath(path: string): string {
  return `${APP_BASE}${path}`;
}
