# Claude OAuth Token Setup Guide

## Claude OAuth トークンの取得方法

### 方法1: Claude API Keyを使用（推奨）

claude-code-action は OAuth の代わりに通常の API Key も使用できます。

1. **Anthropic Console にアクセス**
   - https://console.anthropic.com/ にログイン

2. **API Key を取得**
   - API Keys セクションに移動
   - 新しい API Key を作成またはコピー

3. **GitHub Secrets に設定**
   ```bash
   # API Key をシークレットに設定
   gh secret set ANTHROPIC_API_KEY
   ```

### 方法2: ワークフローを修正して API Key を使用

`.github/workflows/claude.yml` を以下のように修正：

```yaml
- name: Run Claude PR Action
  uses: grll/claude-code-action@beta
  with:
    anthropic_api_key: ${{ secrets.ANTHROPIC_API_KEY }}
    # OAuth 関連の行を削除またはコメントアウト
    # use_oauth: true
    # claude_access_token: ${{ secrets.CLAUDE_ACCESS_TOKEN }}
    # claude_refresh_token: ${{ secrets.CLAUDE_REFRESH_TOKEN }}
    # claude_expires_at: ${{ secrets.CLAUDE_EXPIRES_AT }}
```

### 実装方法

上記の方法1（API Key）を使用することをお勧めします。OAuthよりもシンプルで管理が簡単です。