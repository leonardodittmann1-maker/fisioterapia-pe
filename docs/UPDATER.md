# Atualização do SwipeClean

## Versão 2

1. Compile `gradle :app:assembleRelease` ou baixe o APK sem assinatura do fluxo de compilação.
2. Alinhe o APK e assine com a chave privada de distribuição do SwipeClean 2. Guarde a chave fora do Git.
3. Antes de publicar, confirme com `apksigner verify --print-certs` que o certificado coincide com o APK anterior do canal v2. Confirme também o identificador `com.swipeclean.gallery.v2` e incremente o `versionCode`.
4. Atualize `downloads/SwipeClean-v2.apk` e `updates/v2.json` no mesmo commit. O JSON leva `versionCode`, `versionName`, `notes` e o SHA-256 do arquivo assinado.

O app verifica o SHA-256 do arquivo baixado. O instalador Android verifica a assinatura e pede confirmação.

## Versão 1.7

O canal anterior continua em `downloads/SwipeClean.apk` e `updates/version.json`. A versão 1.7 usava uma chave de depuração do cache do GitHub Actions. A versão 2 tem identificador próprio e pode ser instalada no mesmo aparelho com os dois apps presentes.
