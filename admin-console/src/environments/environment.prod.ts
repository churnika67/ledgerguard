// Goes through api-gateway now, not straight to ledger-service -- /api/ledger is the prefix
// the gateway strips before forwarding, so every call below still reaches the same backend paths.
export const environment = {
  production: true,
  apiBaseUrl: 'https://ledgerguard-api-gateway.onrender.com/api/ledger',
};
