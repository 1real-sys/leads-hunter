# E-mail de contato pelo site oficial

**Estado:** implementado; cobertura em uma amostra real de sites ainda não foi medida.

A ação explícita **Buscar e-mails**, no detalhe de uma busca do Histórico, inicia ou recupera uma execução assíncrona persistida. O POST inicia a ação e o GET acompanha o progresso. Uma busca tem no máximo uma execução ativa, e leads que já têm e-mail são ignorados.

`EmailLeadService` lê somente o site oficial guardado no lead e, se necessário, mais uma página de contato do mesmo host: no máximo duas páginas por lead em cada execução. Antes de gravar, o conteúdo precisa confirmar a identidade do estabelecimento por telefone, endereço com número ou CNPJ, sem evidência conflitante. Só é aceito e-mail do host do site ou de seus subdomínios. Endereços externos descartados aparecem apenas em um contador agregado, sem guardar o endereço rejeitado.

O e-mail aprovado é persistido com data de captura e host de origem, exposto na API e incluído nas exportações. Se o host oficial mudar, o e-mail anterior e seus metadados são limpos; falha de leitura ou omissão do site em uma nova resposta Places não apaga um e-mail válido.

O leitor não segue redirecionamentos, não executa JavaScript e não navega em terceiros. Sites sem evidência textual, com conteúdo bloqueado ou e-mail ofuscado podem ficar sem captura. Ainda falta medir a cobertura em sites públicos reais para decidir se vale ampliar a regra de domínio próprio.

**Referências:** [API](../API.md), [fluxo interno](../fluxo.md) e [histórico de implementações](../HISTORICO_IMPLEMENTACOES.md).
