# Decisões de precisão da pesquisa de site e Instagram
git 
**Estado:** refinamentos implementados; falta medir precisão e cobertura em uma amostra maior.

Este registro concentra as causas que mudaram o classificador. O fluxo completo está em [pesquisa inteligente](pesquisa-inteligente.md).

## Filiais e endereços

No caso Petz Vila Velha, a página da unidade aparecia nos resultados, mas era recusada. O classificador exigia que município e marca estivessem juntos no domínio, agrupava páginas distintas da mesma rede e descartava o caminho da URL. A correção separa marca de unidade, avalia cada página da rede antes de escolher a melhor e preserva a URL específica da filial. Endereço com número deve aparecer na mesma ocorrência; abreviações delimitadas como `Rod.`/`Rodovia` são equivalentes. Telefone com zero de tronco também é normalizado. Conflitos de endereço, município ou telefone continuam vetando candidatos.

## Nomes alternativos e perfis sociais

O caso Supermercado Michel mostrou que a marca pública e o nome cadastrado podem diferir. A avaliação considera sinais independentes como telefone, endereço e CNPJ, sem aceitar um perfil apenas por semelhança de nome ou handle. O caso Hortifruti Castelo ajudou a ajustar endereço parcial e confirmação do perfil; um perfil de outra unidade ou de uma marca genérica não deve ser atribuído ao lead sem evidência da unidade.

Handles com marca e praça são tratados como candidatos, não como prova. A classificação verifica também conflitos explícitos e mantém a distinção entre página de filial e página geral da rede.

## Leitura de páginas candidatas

A pesquisa pode abrir uma URL candidata pública para confirmar telefone, endereço ou CNPJ. O limite atual é de três páginas e três consultas ao Brave por lead. O leitor não segue redirecionamentos nem acessa destinos privados. Páginas bloqueadas ou indisponíveis permanecem inconclusivas.

## Próxima medição

Montar uma amostra rotulada de negócios independentes, redes com filiais, perfis ausentes e homônimos. Medir separadamente links aceitos corretos, links aceitos errados e cobertura; revisar manualmente resultados antes de mudar limiares. Regressões de casos individuais não substituem essa medição.

**Referências:** [fluxo interno](../fluxo.md), [histórico de implementações](../HISTORICO_IMPLEMENTACOES.md).
