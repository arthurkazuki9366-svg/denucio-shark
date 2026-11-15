SharkDenuncias 1.0.0 - Sistema de denúncias para Paper

COMANDOS
/denunciar                      Abre o menu (escolher jogador -> escolher motivo).
/denunciar <jogador>            Abre direto o menu de motivos.
/denunciar <jogador> <motivo>   Envia a denúncia direto.
/denuncias                      Painel de denúncias (somente OP).

REGRAS
- Qualquer jogador pode denunciar. Motivo obrigatório; não é possível denunciar a si mesmo.
- Só jogadores com OP recebem a notificação e abrem o painel.
- O denunciado nunca é avisado. Se um OP for o denunciado, ele também não vê aquela denúncia.
- Painel: clique esquerdo = resolvida/reabrir; Shift + clique direito = excluir.
- Denúncias ficam salvas em plugins/SharkDenuncias/denuncias.yml.
- Configurações (cooldown, motivos rápidos etc.) em plugins/SharkDenuncias/config.yml.

COMPILAÇÃO
JDK 17 e Gradle 8.7+: gradle build  ->  build/libs/SharkDenuncias-1.0.0.jar
Ou envie o projeto para o GitHub e baixe o JAR na aba Actions.

COMPATIBILIDADE
Compilado contra Paper API 1.20.4, usando só APIs estáveis, para rodar em Paper 1.20+.
