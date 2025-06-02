#!/bin/bash

echo "🔐 Setting up Claude OAuth tokens for GitHub"
echo ""
echo "To get these tokens, you need to:"
echo "1. Go to https://console.anthropic.com/"
echo "2. Set up OAuth for your application"
echo "3. Get your access token, refresh token, and expiration timestamp"
echo ""

# Set Claude Access Token
echo "Please enter your Claude OAuth Access Token:"
read -s CLAUDE_ACCESS_TOKEN
echo $CLAUDE_ACCESS_TOKEN | gh secret set CLAUDE_ACCESS_TOKEN

# Set Claude Refresh Token
echo ""
echo "Please enter your Claude OAuth Refresh Token:"
read -s CLAUDE_REFRESH_TOKEN
echo $CLAUDE_REFRESH_TOKEN | gh secret set CLAUDE_REFRESH_TOKEN

# Set Claude Token Expiration
echo ""
echo "Please enter your Claude Token Expires At (timestamp):"
read CLAUDE_EXPIRES_AT
echo $CLAUDE_EXPIRES_AT | gh secret set CLAUDE_EXPIRES_AT

echo ""
echo "✅ Claude OAuth secrets have been configured!"
echo ""
echo "📋 Configured secrets:"
echo "  - CLAUDE_ACCESS_TOKEN"
echo "  - CLAUDE_REFRESH_TOKEN"
echo "  - CLAUDE_EXPIRES_AT"
echo ""
echo "🚀 The Claude PR Assistant should now work when @claude is mentioned in issues!"