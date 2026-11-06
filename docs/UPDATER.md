# Atualização do SwipeClean

- V1.4: download por HTTPS usando executor independente, verificação do arquivo completo com SHA-256 e fornecimento do APK para o instalador Android por ContentProvider dedicado (somente leitura).
- A assinatura debug do aplicativo é configurada explicitamente para a chave do cache da compilação. O workflow restaura a chave antes do build. Caches podem expirar e não substituem armazenamento permanente de chaves; antes de cada publicação, comparar o certificado com a versão anterior.
- Versões até 1.3 foram produzidas com certificados debug diferentes. O Android pode exigir desinstalação para a transição à nova chave. Desinstalar limpa dados salvos no app. Não promete migração quando as assinaturas forem diferentes.
- Versões futuras só podem ser instaladas por cima quando mantiverem **applicationId** e certificado de assinatura idênticos e **versionCode** crescente.
- A instalação requer confirmação do usuário, conforme regras do Android.
