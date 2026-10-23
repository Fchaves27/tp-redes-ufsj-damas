# Plugin de damas online para Pitiupi

O plugin conecta o tabuleiro web existente ao Pitiupi por uma ponte WebSocket
local. As mensagens entre jogadores usam o multicast UDP do Pitiupi.

## Compilar e instalar

Requer JDK 17 e Apache Ant. Na raiz `tp-redes-ufsj-damas`:

```sh
cd pitiupi/pitiupi
ant jar

cd ../pitiupi.damas
ant jar
mkdir -p ../pitiupi/plugins
cp dist/pitiupi.damas.jar ../pitiupi/plugins/

cd ../pitiupi
ant run
```

Faça isso em cada computador. O plugin deve estar em `plugins/` no diretório
de execução do Pitiupi. O JAR do tabuleiro inclui a página de `damas/`, copiada
durante a compilação.

## Jogar pela rede

1. Em cada computador, abra o Pitiupi com o mesmo plugin e entre com nomes
   diferentes.
2. No menu **Jogos > Abrir jogo de damas**, abra o tabuleiro em cada instância.
   A página precisa ser aberta por esse menu para conectar à ponte local.
3. No tabuleiro de quem vai criar a partida, selecione **Online Pitiupi** e
   clique **Criar sala**.
4. Compartilhe o código da sala com o outro jogador. No tabuleiro dele,
   selecione **Online Pitiupi**, informe o código e clique **Entrar**. Convites
   recebidos também aparecem na página e preenchem o campo do código.
5. As brancas começam. Cada jogador só pode mover as próprias peças; **Novo
   jogo** reinicia o tabuleiro para os dois.

Os computadores precisam estar na mesma rede com multicast UDP e a porta
configurada no Pitiupi permitidos pelo firewall. A ponte do navegador escuta
somente em `127.0.0.1:8765`; não é necessário abrir essa porta na rede. Use
uma aba do jogo por instância local do Pitiupi.
O transporte Pitiupi usa UDP multicast, portanto a entrega de jogadas depende
da rede local e não é garantida em redes que filtrem multicast.
