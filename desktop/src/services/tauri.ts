let cachedInvoke: any = null;

export async function getInvoke(): Promise<any> {
  if (cachedInvoke) return cachedInvoke;
  try {
    const { invoke } = await import('@tauri-apps/api/core');
    cachedInvoke = invoke;
    return invoke;
  } catch (err) {
    console.warn('[Idroid IPC] Tauri core invoke unavailable, running in preview mode:', err);
    return null;
  }
}

export async function safeInvoke<T>(cmd: string, args: Record<string, any> = {}): Promise<T | null> {
  const inv = await getInvoke();
  if (!inv) return null;
  try {
    return await inv(cmd, args) as T;
  } catch (err) {
    console.error(`[Idroid IPC Error] ${cmd}:`, err);
    throw err;
  }
}

let cachedListen: any = null;

export async function getListen(): Promise<any> {
  if (cachedListen) return cachedListen;
  try {
    const { listen } = await import('@tauri-apps/api/event');
    cachedListen = listen;
    return listen;
  } catch (err) {
    return null;
  }
}
