# Claude OAuth Token 取得手順

## OAuth トークンの取得方法

1. **Anthropic Console にアクセス**
   - https://console.anthropic.com/ にログイン

2. **OAuth アプリケーションの設定**
   - Settings → API → OAuth Applications
   - 新しい OAuth アプリケーションを作成（まだない場合）
   - Redirect URI: `https://github.com/`

3. **OAuth フローの実行**
   - Authorization URL にアクセス:
     ```
     https://console.anthropic.com/oauth/authorize?client_id=YOUR_CLIENT_ID&redirect_uri=https://github.com/&response_type=code&scope=read,write
     ```
   - 認証後、GitHub にリダイレクトされ、URL に `code` パラメータが含まれます

4. **トークンの交換**
   - 取得した code を使って access_token を取得
   - curl や Postman で POST リクエスト:
     ```bash
     curl -X POST https://api.anthropic.com/oauth/token \
       -H "Content-Type: application/json" \
       -d '{
         "grant_type": "authorization_code",
         "code": "YOUR_AUTH_CODE",
         "client_id": "YOUR_CLIENT_ID",
         "client_secret": "YOUR_CLIENT_SECRET",
         "redirect_uri": "https://github.com/"
       }'
     ```

5. **レスポンスから以下を取得**
   - `access_token`: CLAUDE_ACCESS_TOKEN
   - `refresh_token`: CLAUDE_REFRESH_TOKEN
   - `expires_at`: CLAUDE_EXPIRES_AT (Unix timestamp)

## GitHub Secrets への設定

取得したトークンを GitHub に設定：

```bash
# Access Token
echo "YOUR_ACCESS_TOKEN" | gh secret set CLAUDE_ACCESS_TOKEN

# Refresh Token  
echo "YOUR_REFRESH_TOKEN" | gh secret set CLAUDE_REFRESH_TOKEN

# Expires At (Unix timestamp)
echo "YOUR_EXPIRES_AT" | gh secret set CLAUDE_EXPIRES_AT
```

## トークンの更新

期限切れの場合は refresh_token を使って更新：

```bash
curl -X POST https://api.anthropic.com/oauth/token \
  -H "Content-Type: application/json" \
  -d '{
    "grant_type": "refresh_token",
    "refresh_token": "YOUR_REFRESH_TOKEN",
    "client_id": "YOUR_CLIENT_ID",
    "client_secret": "YOUR_CLIENT_SECRET"
  }'
```