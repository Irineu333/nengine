# Tasks — Debug UI Draggable

## 1. Consumo de drag no Input
- [ ] 1.1 Adicionar ao `Input` um flag de consumo de arrasto (espelhando
  `mouseClickConsumed`): default não-consumido, resetado por tick no mesmo
  ponto do pipeline
- [ ] 1.2 KDoc do flag; documentar o contrato "checar antes de pan/arraste de
  gameplay"
- [ ] 1.3 Varrer os jogos shipped (ex.: pool8 "puxar e soltar") e adotar o
  flag onde houver arraste de mundo

## 2. Arrasto de painel via polling
- [ ] 2.1 Lógica de arrasto no painel base de debug: pega no topo/título,
  `grabOffset` ao iniciar, `origin = pointer - grabOffset` enquanto
  `isMouseDown`, encerrar ao soltar
- [ ] 2.2 Hit-test da zona de pega via `screenRect()` do painel
- [ ] 2.3 Setar o flag de drag-consumido enquanto arrastando
- [ ] 2.4 Distinguir pega-de-arrasto de clique-de-conteúdo (botões do Time HUD
  continuam clicáveis)

## 3. Override de posição sobre o slot
- [ ] 3.1 Painel guarda override de posição custom (default: ausente → segue slot)
- [ ] 3.2 `DebugDock` respeita o override quando presente; empilha no slot só
  os painéis sem override
- [ ] 3.3 Gesto de reset: limpa o override de um painel (e variante "resetar
  todos") → volta a fluir no slot

## 4. Memória de sessão + re-clamp
- [ ] 4.1 Override sobrevive ao toggle on/off do widget
- [ ] 4.2 Override sobrevive ao `tree.resize`, re-clampado para dentro do viewport
- [ ] 4.3 Confirmar que não há persistência em disco (escopo Fase 3)

## 5. Testes + validação
- [ ] 5.1 Teste: arrastar um painel atualiza sua posição e seta o flag de drag
- [ ] 5.2 Teste: arrasto consumido não vaza para um consumidor de arraste de gameplay
- [ ] 5.3 Teste: posição custom sobrevive ao toggle e é re-clampada no resize
- [ ] 5.4 Teste: reset limpa o override e o painel volta ao slot do dock
- [ ] 5.5 Teste: botão do Time HUD continua clicável (pega só no topo)
- [ ] 5.6 Validação visual manual: arrastar, soltar, redimensionar janela, resetar
