# Tema claro e escuro do frontend

**Estado:** implementado.

O frontend oferece as opções Sistema, Claro e Escuro. A escolha fica no armazenamento local do navegador; Sistema acompanha a preferência do sistema operacional. O tema é aplicado antes de o Angular renderizar a interface para evitar um flash da paleta errada.

A interface usa tokens semânticos para superfícies, texto, bordas e estados. O seletor fica no shell e vale para Busca, Kanban, Histórico, Bloqueios e páginas auxiliares. No mapa Leaflet, a aparência dos tiles acompanha o tema; as cores das faixas de IDHM mantêm o significado da legenda.

**Referências:** [estado do tema](../frontend/src/app/core/theme/tema-store.ts), [fluxo do projeto](../fluxo.md) e [histórico de implementações](../HISTORICO_IMPLEMENTACOES.md).
