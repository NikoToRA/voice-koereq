#\!/bin/bash

echo "🔐 Setting up Azure and GitHub secrets for voice-koereq"

# Check if Azure CLI is installed
if \! command -v az &> /dev/null; then
    echo "❌ Azure CLI is not installed. Please install it first."
    exit 1
fi

# Check if GitHub CLI is installed
if \! command -v gh &> /dev/null; then
    echo "❌ GitHub CLI is not installed. Please install it first."
    exit 1
fi

# Azure login
echo "📝 Logging into Azure..."
az login

# Get subscription ID
SUBSCRIPTION_ID=$(az account show --query id -o tsv)
echo "✅ Using subscription: $SUBSCRIPTION_ID"

# Create service principal
echo "🔨 Creating service principal..."
az ad sp create-for-rbac --name "voice-koereq-github" \
  --role contributor \
  --scopes /subscriptions/$SUBSCRIPTION_ID \
  --sdk-auth > azure-creds.json

# Set GitHub secret for Azure credentials
echo "🔐 Setting AZURE_CREDENTIALS secret..."
gh secret set AZURE_CREDENTIALS < azure-creds.json
rm azure-creds.json

# Set Claude API key
echo "🤖 Setting ANTHROPIC_API_KEY secret..."
echo "Please enter your Anthropic API key:"
read -s ANTHROPIC_API_KEY
echo $ANTHROPIC_API_KEY | gh secret set ANTHROPIC_API_KEY

# Set Azure OpenAI credentials
echo "🧠 Setting Azure OpenAI secrets..."
echo "Please enter your Azure OpenAI endpoint (e.g., https://your-resource.openai.azure.com/):"
read AZURE_OPENAI_ENDPOINT
echo $AZURE_OPENAI_ENDPOINT | gh secret set AZURE_OPENAI_ENDPOINT

echo "Please enter your Azure OpenAI API key:"
read -s AZURE_OPENAI_KEY
echo $AZURE_OPENAI_KEY | gh secret set AZURE_OPENAI_KEY

echo "✅ All secrets have been configured\!"
echo ""
echo "📋 Configured secrets:"
echo "  - AZURE_CREDENTIALS"
echo "  - ANTHROPIC_API_KEY"
echo "  - AZURE_OPENAI_ENDPOINT"
echo "  - AZURE_OPENAI_KEY"
echo ""
echo "🚀 You can now run the GitHub Actions workflows\!"
