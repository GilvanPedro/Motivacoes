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
const PASSWORD_KEY = 'motivacional.admin';

export const auth = {
  get(): string | null {
    try {
      return localStorage.getItem(PASSWORD_KEY);
    } catch {
      return null;
    }
  },
  set(password: string) {
    try {
      localStorage.setItem(PASSWORD_KEY, password);
    } catch {
      /* navegação privada: só fica na sessão atual */
    }
  },
  clear() {
    try {
      localStorage.removeItem(PASSWORD_KEY);
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
  const password = auth.get();
  if (password && !headers.has('X-Admin-Password')) headers.set('X-Admin-Password', password);

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

  checkPassword: (password: string) =>
    request<void>('/api/auth/check', { method: 'POST', headers: { 'X-Admin-Password': password } }),
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
