# Tutorial — Ativar Device Owner (Quiosque Real) no Tablet

Este tutorial mostra como transformar o tablet do Achados e Perdidos em um
quiosque **inquebrável**: o app abre sozinho ao ligar, não pode ser fechado
pelos botões, e só sai com o PIN do gestor.

> ⚠️ **Por que precisa de factory reset?** O Android só deixa um app virar
> Device Owner se **NÃO houver nenhuma conta** (Google, Samsung, etc) configurada
> no aparelho. Como o tablet provavelmente já foi usado, a única forma garantida
> de remover tudo é factory reset.

---

## Requisitos

- Tablet Samsung (ou qualquer Android 7+ com Min SDK 24).
- Cabo USB.
- Um PC com **ADB instalado** (Android Platform Tools).
  - Baixar: <https://developer.android.com/tools/releases/platform-tools>
  - Descompactar em uma pasta (ex: `C:\platform-tools\`) e adicionar ao PATH,
    ou rodar `adb` direto de lá.
- APK do app (`AchadosPerdidos-Tablet.apk`) já com a `BASE_URL_PADRAO` apontando
  para o IP correto do servidor na rede da escola.

---

## Passo 1 — Reset de fábrica no tablet

1. No tablet: **Configurações → Geral → Restaurar / Redefinir → Redefinição
   para o padrão de fábrica**.
   - Em Samsung: `Configurações → Gerenciamento geral → Restaurar → Restaurar
     padrão de fábrica`.
2. Confirme. O tablet vai reiniciar e apagar tudo (pode levar 5–10 min).

---

## Passo 2 — Setup inicial SEM conta Google

Esse é o passo mais importante e onde **a maioria dos tutoriais falha**:

1. Quando o tablet ligar, comece o setup normal:
   - Idioma → Português
   - Termos → Aceitar
   - **Wi-Fi:** você pode conectar à rede do colégio, mas...
2. Quando aparecer **"Copiar apps e dados"** ou **"Fazer login na conta
   Google"**, escolha **PULAR** / **NÃO COPIAR** / **CONFIGURAR COMO NOVO**.
3. Continue sem adicionar **nenhuma conta** (nem Google, nem Samsung, nem nada).
4. Termine o setup — você deve cair na tela inicial do Android limpa.

> Se aparecer um pop-up insistindo em conta Google, escolha "Pular" ou
> "Configurar mais tarde". **NÃO** logue.

---

## Passo 3 — Ativar Opções do Desenvolvedor + USB Debug

1. **Configurações → Sobre o tablet → Informações do software**.
2. Toque **7 vezes** em "Número da compilação" (Build Number). Vai aparecer
   "Você agora é um desenvolvedor!".
3. Volte. Agora aparece um novo menu **"Opções do desenvolvedor"**.
4. Entre nele e ative:
   - ✅ **Depuração USB**
   - ✅ **Permanecer ativo** (tela não desliga enquanto carrega — útil pro quiosque)

---

## Passo 4 — Conectar o tablet ao PC e autorizar

1. Conecte o tablet ao PC com o cabo USB.
2. No tablet, vai aparecer um pop-up **"Permitir depuração USB?"** — marque
   **"Sempre permitir deste computador"** e toque em **Permitir**.
3. No PC, abra um terminal e teste:

   ```powershell
   adb devices
   ```

   Deve aparecer algo como:

   ```
   List of devices attached
   R52N12345A    device
   ```

   Se aparecer `unauthorized`, refaça o passo 2.

---

## Passo 5 — Instalar o APK do Achados e Perdidos

No terminal do PC:

```powershell
adb install -r "C:\Users\gabir\Downloads\AchadosPerdidos-Tablet.apk"
```

Deve aparecer `Success`. Confira no tablet que o app **Achados e Perdidos** está
na lista de apps (não abra ainda).

---

## Passo 6 — Promover o app a Device Owner

**Este é o comando mágico.** Funciona **uma única vez** por vida do aparelho
(até o próximo factory reset).

```powershell
adb shell dpm set-device-owner com.escola.achadosperdidos/.AdminReceiver
```

Resposta esperada:

```
Success: Device owner set to package ComponentInfo{com.escola.achadosperdidos/com.escola.achadosperdidos.AdminReceiver}
Active admin: ComponentInfo{com.escola.achadosperdidos/com.escola.achadosperdidos.AdminReceiver}
```

### Se aparecer erro

| Mensagem | Causa | Solução |
|----------|-------|---------|
| `Not allowed to set the device owner because there are already several users` | Existe uma conta de usuário ou perfil ativo. | Confirme que não há contas (Configurações → Contas → não deve haver nada). Se houver, remova e tente de novo. Última opção: factory reset. |
| `Neither user 0 nor current process has android.permission.MANAGE_DEVICE_ADMINS` | Setup do device não terminou direito. | Termine completamente o setup do Android (apertar Home pelo menos uma vez sem ficar em telas pendentes). |
| `Trying to set the device owner, but device owner is already set` | Já foi promovido antes (ex: você tentou ontem). | Já está pronto, pode pular esse passo. |
| `Failed to set device owner` (genérico) | Conta Google sobreviveu ao setup. | Factory reset de novo e refaça Passo 2 com muito cuidado. |

---

## Passo 7 — Verificar que funcionou

```powershell
adb shell dumpsys device_policy | findstr "Device Owner"
```

Saída esperada:

```
  Device Owner:
    admin=ComponentInfo{com.escola.achadosperdidos/com.escola.achadosperdidos.AdminReceiver}
```

Agora abra o app no tablet (apertando o ícone). Você deve notar:

- ✅ A barra de status (relógio, bateria, Wi-Fi) **sumiu**.
- ✅ Botões Home / Recents / Voltar **não funcionam** — apertar não sai do app.
- ✅ Volume não abre a tela de ajustes.
- ✅ Swipe-down não puxa notificações.

Para sair: **segure o mascote por 3 segundos** → digite o PIN `chiara123`.

---

## Passo 8 — Testar o boot automático

1. **Desligue completamente o tablet** (segurar power → Desligar).
2. **Ligue novamente.**
3. Após o boot, sem você tocar em nada, o app **Achados e Perdidos** deve
   abrir sozinho em modo quiosque.

Se demorar mais de 30s ou não abrir, refaça o `dpm set-device-owner` (Passo 6) —
às vezes a primeira vez não pega imediatamente.

---

## Como atualizar o app no futuro

**Mesmo sendo Device Owner**, você pode atualizar o APK:

```powershell
adb install -r "C:\Users\gabir\Downloads\AchadosPerdidos-Tablet.apk"
```

A flag `-r` (reinstall) preserva o estado de Device Owner — não precisa refazer
o tutorial inteiro a cada update.

---

## Como REMOVER o Device Owner (se precisar)

⚠️ **Device Owner não pode ser removido por código nem pelo usuário no tablet.**
A única forma é factory reset.

Antes de fazer factory reset:

```powershell
adb shell dpm remove-active-admin com.escola.achadosperdidos/.AdminReceiver
```

> Isso **só funciona se** o app explicitamente liberar a remoção pelo
> `clearDeviceOwnerApp()` no código. Senão, vai falhar com permission denied.

### Solução de emergência — Factory reset físico

Se o Device Owner estiver "preso" e você não conseguir mais entrar no app:

1. Desligue o tablet.
2. Segure **Power + Volume Up** (ou Power + Bixby + Volume Up em Samsung
   novos) até aparecer o **Recovery Mode**.
3. Navegue com Volume até **"Wipe data / factory reset"**.
4. Confirme com o botão Power.
5. O tablet vai voltar de fábrica — refaça este tutorial desde o início.

---

## Notas técnicas (para o dev)

- A whitelist de pacotes para lock task é feita em `MainActivity.aplicarPoliticasDeviceOwner()`
  via `dpm.setLockTaskPackages(...)`.
- A status bar é desabilitada permanentemente com `dpm.setStatusBarDisabled(true)`.
- O app vira HOME default via `addPersistentPreferredActivity` com filtro
  `CATEGORY_HOME + CATEGORY_DEFAULT`.
- Restrições de usuário aplicadas: `no_safe_boot`, `no_factory_reset`,
  `no_add_user`, `no_install_unknown_sources`. Isso impede até quem souber o
  PIN técnico de pular o quiosque.
- O `BootReceiver` é redundância caso a HOME default não pegue: garante que
  qualquer `BOOT_COMPLETED` reabra o app.
- Compatibilidade: Min SDK 24 (Android 7.0). Algumas restrições só pegam a
  partir de Android 9 (`LOCK_TASK_FEATURE_KEYGUARD`).
