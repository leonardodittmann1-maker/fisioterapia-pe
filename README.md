# Meu Pé • Fisio — Android

Aplicativo pessoal offline para organizar lembretes de fisioterapia e registrar a evolução da dor do pé.

## Funções (v1.0.0)
- Rotina com quatro fases e exercícios descritos em português.
- Registro da dor (0–10), tempo em pé/caminhada, observações e sessões.
- Histórico local, exportação por compartilhamento e lembretes locais configuráveis.
- Bloqueio inicial de exercícios de fortalecimento/carga até liberação por profissional.
- Avisos de segurança quando há piora importante ou dificuldade para caminhar.

**Atenção:** não é diagnóstico nem prescrição de tratamento. Dor pontual no navicular com crises que causam mancar exige avaliação para descartar lesões ósseas antes de exercícios com carga.

## Criar o APK pelo GitHub
Abra **Actions → Gerar APK Android → última execução → Artifacts** e baixe `fisioterapia-pe-v1-debug-apk`. Extraia o arquivo ZIP e instale `app-debug.apk` no Android. O APK debug serve para testes; para distribuição futura, use APK assinado com chave própria.

O build é acionado automaticamente a cada atualização da branch main ou manualmente pelo botão **Run workflow**.

## Privacidade
Registros ficam no armazenamento privado do app no dispositivo. Não há conta, servidor ou envio automático de dados ao GitHub. A opção Exportar compartilha um relatório somente quando você a aciona. Se desinstalar o aplicativo, os dados podem ser apagados; exporte periodicamente.

## Tecnologia
Android nativo Java, Android Gradle Plugin 8.7.3, JDK 17 e Gradle 8.9. Sem dependências externas de runtime.
