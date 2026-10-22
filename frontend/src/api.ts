export interface Tag {
  id: number;
  name: string;
  videoCount: number;
}

export interface TagRef {
  id: number;
  name: string;
}

export type VideoSource = 'YOUTUBE' | 'UPLOAD';

export interface Video {
  id: number;
  title: string;
  source: VideoSource;
  youtubeId: string | null;
  url: string | null;
  tags: TagRef[];
  createdAt: string;
}

export interface CreateVideo {
  source: VideoSource;
  youtubeUrl?: string;
  storageKey?: string;
  title?: string;
  tagIds: number[];
}

// Vazio = mesma origem (proxy da Vercel / Vite). Defina VITE_API_URL para apontar direto pro backend.
const BASE = ((import.meta.env.VITE_API_URL as string | undefined) ?? '').replace(/\/$/, '');
export interface Session {
  token: string;
  expiresAt: string;
}

const SESSION_KEY = 'motivacoes.session';
let memorySession: Session | null = null; // fallback se o localStorage estiver bloqueado

try {
  localStorage.removeItem('motivacional.admin'); // versão antiga guardava a senha em texto
} catch {
  /* ignore */
}

/** Sessão de admin salva no navegador; dura até o servidor dizer que expirou (6 meses). */
export const auth = {
  get(): string | null {
    let session = memorySession;
    try {
      const raw = localStorage.getItem(SESSION_KEY);
      if (raw) session = JSON.parse(raw) as Session;
    } catch {
      /* ignore */
    }
    if (!session) return null;
    if (Date.parse(session.expiresAt) <= Date.now()) {
      auth.clear();
      return null;
    }
    return session.token;
  },
  set(session: Session) {
    memorySession = session;
    try {
      localStorage.setItem(SESSION_KEY, JSON.stringify(session));
    } catch {
      /* navegação privada: só fica nesta aba */
    }
  },
  clear() {
    memorySession = null;
    try {
      localStorage.removeItem(SESSION_KEY);
    } catch {
      /* ignore */
    }
  },
};

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const token = auth.get();
  if (token) headers.set('Authorization', `Bearer ${token}`);

  let res: Response;
  try {
    res = await fetch(BASE + path, { ...init, headers });
  } catch {
    throw new ApiError(0, 'Não foi possível conectar ao servidor.');
  }
  if (!res.ok) {
    let message = `Erro ${res.status}`;
    try {
      const body = await res.json();
      if (body?.error) message = body.error;
    } catch {
      /* resposta sem JSON */
    }
    throw new ApiError(res.status, message);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

const json = (body: unknown) => JSON.stringify(body);

export const api = {
  config: () => request<{ uploadsEnabled: boolean }>('/api/config'),

  tags: () => request<Tag[]>('/api/tags'),
  createTag: (name: string) => request<Tag>('/api/tags', { method: 'POST', body: json({ name }) }),
  deleteTag: (id: number) => request<void>(`/api/tags/${id}`, { method: 'DELETE' }),

  videos: (tagId?: number) => request<Video[]>(`/api/videos${tagId ? `?tagId=${tagId}` : ''}`),
  randomVideo: (tagId: number, excludeId?: number) =>
    request<Video>(`/api/videos/random?tagId=${tagId}${excludeId ? `&exclude=${excludeId}` : ''}`),
  createVideo: (body: CreateVideo) => request<Video>('/api/videos', { method: 'POST', body: json(body) }),
  updateVideo: (id: number, body: { title?: string; tagIds?: number[] }) =>
    request<Video>(`/api/videos/${id}`, { method: 'PUT', body: json(body) }),
  deleteVideo: (id: number) => request<void>(`/api/videos/${id}`, { method: 'DELETE' }),

  login: (password: string) => request<Session>('/api/auth/login', { method: 'POST', body: json({ password }) }),
  checkSession: () => request<void>('/api/auth/check', { method: 'POST' }),
  presignUpload: (fileName: string, contentType: string) =>
    request<{ uploadUrl: string; key: string }>('/api/uploads/presign', {
      method: 'POST',
      body: json({ fileName, contentType }),
    }),
};

/** Envia o arquivo direto pro bucket (URL assinada), reportando o progresso de 0 a 100. */
export function uploadFile(
  url: string,
  file: File,
  contentType: string,
  onProgress: (percent: number) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url);
    xhr.setRequestHeader('Content-Type', contentType);
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100));
    };
    xhr.onload = () =>
      xhr.status >= 200 && xhr.status < 300
        ? resolve()
        : reject(new ApiError(xhr.status, `Falha no envio do arquivo (${xhr.status}).`));
    xhr.onerror = () =>
      reject(new ApiError(0, 'Falha no envio do arquivo. Confira o CORS do bucket (veja o README).'));
    xhr.send(file);
  });
}
