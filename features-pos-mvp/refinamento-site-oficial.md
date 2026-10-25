# Site oficial como ponto de partida da pesquisa

**Estado:** implementado; validação positiva em sites públicos reais ainda é limitada por bloqueios e indisponibilidade externa.

O `websiteUri` devolvido pelo Google Places é salvo no campo `website` do lead (migration V7) e exibido na API, no detalhe e nas exportações. Se uma nova resposta do Places omitir o site, um valor conhecido é preservado.

Na ação **Buscar informações**, a pesquisa tenta primeiro o site oficial conhecido. A página pode fornecer links de Instagram e evidências de identidade da unidade. Esses links continuam sujeitos ao classificador: estar no site não dispensa a confirmação por telefone, endereço ou CNPJ. Se não houver confirmação e o usuário tiver habilitado o Brave Search para a execução, a pesquisa pode consultar o Brave. O site aberto conta no limite de três páginas por lead.

O leitor aceita apenas HTTP/HTTPS público, não segue redirecionamentos e limita tempo e tamanho da resposta. Sites que devolvem 403, dependem de JavaScript ou ficam indisponíveis podem não fornecer links; essa falha não prova que o estabelecimento não tem Instagram. Cenários controlados confirmaram o fluxo sem Brave, mas a extração positiva em páginas públicas bloqueadas não foi demonstrada.

**Referências:** [pesquisa inteligente](pesquisa-inteligente.md), [API](../API.md) e [fluxo interno](../fluxo.md).
