# Motivação

Site de vídeos motivacionais organizados por tags. Na página inicial cada tag vira um botão:
clicou, abre um vídeo **aleatório** daquela tag em **tela cheia**, já tocando. O botão **↻ Outro**
sorteia outro vídeo da mesma tag.

Em `/admin.html` (protegido por senha) você cadastra vídeos por **link do YouTube** ou **enviando o
arquivo**, cria/apaga tags e edita as tags de cada vídeo.

```
motivacional/
├── backend/    Java 21 + Spring Boot 3.5 (API REST, Neon/Postgres, Flyway, upload p/ R2)
└── frontend/   HTML + CSS + TypeScript (Vite), deploy na Vercel
```

## Como as peças se conectam

```
Navegador ──► Vercel (frontend estático)
                │  /api/*  (rewrite no vercel.json)
                ▼
             Render (backend Java) ──► Neon (tags, vídeos)
                │
                └─ gera URL assinada ──► navegador envia o arquivo direto pro Cloudflare R2
```

- **A Vercel não roda Java**, por isso o backend fica no Render (plano grátis, via Docker). A Vercel
  repassa `/api/*` pro Render, então o navegador só fala com um domínio (sem dor de cabeça com CORS).
- **Vídeos enviados não vão pro Neon** (o banco grátis tem 0,5 GB). Ficam no Cloudflare R2
  (10 GB grátis, sem custo de download); no banco fica só a referência.

## 1. Neon (banco)

1. Crie um projeto em <https://console.neon.tech>.
2. Em **Connect**, desligue *Connection pooling* e copie a connection string inteira.
3. Cole numa única variável, do jeito que veio:
   ```
   DATABASE_URL=postgresql://neondb_owner:senha@ep-xxxx.us-east-2.aws.neon.tech/neondb?sslmode=require
   ```
As tabelas são criadas automaticamente pelo Flyway na primeira vez que o backend sobe.

## 2. Cloudflare R2 (opcional, só pra enviar arquivos)

Sem isso o site funciona normalmente, só com links do YouTube.

1. No painel da Cloudflare: **R2 → Create bucket** (ex.: `motivacional`).
2. No bucket, **Settings → Public access**: ative o **r2.dev subdomain** (ou conecte um domínio
   seu). Essa URL é o `STORAGE_PUBLIC_URL`.
3. Ainda em Settings, **CORS policy**, cole (troque pelo seu domínio da Vercel):
   ```json
   [
     {
       "AllowedOrigins": ["http://localhost:5173", "https://SEU-SITE.vercel.app"],
       "AllowedMethods": ["PUT", "GET"],
       "AllowedHeaders": ["Content-Type"],
       "MaxAgeSeconds": 3600
     }
   ]
   ```
4. **R2 → Manage API tokens → Create API token** com permissão *Object Read & Write* nesse bucket.
   Anote o Access Key ID, o Secret e o endpoint `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`.

```
STORAGE_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com
STORAGE_REGION=auto
STORAGE_BUCKET=motivacional
STORAGE_ACCESS_KEY=...
STORAGE_SECRET_KEY=...
STORAGE_PUBLIC_URL=https://pub-xxxxxxxx.r2.dev
```

> Também funciona com AWS S3, Backblaze B2 ou qualquer serviço compatível com S3: é só trocar o
> endpoint e a região.

## 3. Rodando no seu computador

Pré-requisitos: Java 21+, Maven, Node 20+.

```bash
# backend
cd backend
cp .env.example .env      # preencha com os dados do Neon, a senha de admin e (opcional) o R2
mvn spring-boot:run       # sobe em http://localhost:8080

# frontend (outro terminal)
cd frontend
npm install
npm run dev               # abre em http://localhost:5173 (o /api vai pro backend local)
```

Acesse <http://localhost:5173/admin.html>, entre com a `ADMIN_PASSWORD`, crie uma tag e adicione um vídeo.

## 4. Deploy

Primeiro suba o projeto pro GitHub (o Render e a Vercel puxam de lá).

### Backend no Render

O repositório tem um `render.yaml`, então o Render já sabe como subir o backend.

1. <https://render.com> → **New → Blueprint** → conecte o repositório.
2. O Render lê o `render.yaml` e pede só dois valores: `DATABASE_URL` (a string do Neon inteira)
   e `ADMIN_PASSWORD`. Preencha e clique em **Apply**.
3. Se for usar upload, depois adicione as `STORAGE_*` em **Environment** no painel do serviço.
4. Depois do deploy, anote a URL (ex.: `https://motivacoes-api.onrender.com`).

> O plano grátis do Render "dorme" depois de 15 min sem uso; o primeiro acesso depois disso demora
> uns 30–60 s (o site mostra "Acordando o servidor…"). Para evitar, dá pra usar um serviço como o
> UptimeRobot chamando `/api/health` a cada 10 min, ou um plano pago.

### Frontend na Vercel

1. Em `frontend/vercel.json`, troque `https://SEU-BACKEND.onrender.com` pela URL do Render e faça commit.
2. <https://vercel.com> → **Add New → Project** → importe o repositório.
3. **Root Directory:** `frontend` · Framework: **Vite** (detectado sozinho).
4. **Não precisa de nenhuma variável de ambiente na Vercel.** Deploy. Coloque o domínio da Vercel no CORS do bucket R2 (passo 2.3).

## API

Leitura é pública; `POST`/`PUT`/`DELETE` exigem o header `X-Admin-Password`.

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/tags` | Tags com quantidade de vídeos |
| POST | `/api/tags` | `{ "name": "workout" }` |
| DELETE | `/api/tags/{id}` | Apaga a tag (os vídeos ficam) |
| GET | `/api/videos?tagId=` | Lista vídeos (filtro opcional) |
| GET | `/api/videos/random?tagId=&exclude=` | Vídeo aleatório da tag (evita repetir o `exclude`) |
| POST | `/api/videos` | `{ "source": "YOUTUBE", "youtubeUrl": "...", "tagIds": [1] }` ou `{ "source": "UPLOAD", "storageKey": "videos/...", "title": "...", "tagIds": [1] }` |
| PUT | `/api/videos/{id}` | `{ "title": "...", "tagIds": [1, 2] }` |
| DELETE | `/api/videos/{id}` | Apaga o vídeo (e o arquivo no R2, se for upload) |
| POST | `/api/uploads/presign` | `{ "fileName", "contentType" }` → `{ uploadUrl, key }` |
| GET | `/api/config` | `{ uploadsEnabled }` |

Links do YouTube aceitos: `watch?v=`, `youtu.be/`, `shorts/`, `embed/`, `live/` ou o ID direto.
Se o título ficar em branco, ele é buscado no YouTube automaticamente.

## Observações sobre tela cheia e autoplay

- No **PC e Android** o vídeo abre em tela cheia de verdade e já começa com som.
- No **iPhone** o Safari não deixa sites colocarem em tela cheia nada além do próprio player de
  vídeo, então o site ocupa a tela inteira como um overlay. Se o navegador bloquear o som no
  autoplay, o vídeo começa mudo e aparece o botão **🔊 Ativar som**.
- Alguns vídeos do YouTube têm a incorporação desativada pelo dono; nesses o player avisa e você
  pode tocar em **↻ Outro**.
- **Baixar vídeos do YouTube** pra reenviar aqui pode violar os termos do YouTube e direitos
  autorais. Prefira usar o link, ou envie arquivos que você tem direito de usar.
