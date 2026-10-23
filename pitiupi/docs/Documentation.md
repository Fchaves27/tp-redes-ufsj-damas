# Visão geral

O `Pitiupi` é uma plataforma P2P baseada em uma arquitetura de plugins, 
desenvolvida para permitir a criação de aplicações distribuídas sobre uma 
infraestrutura comum de comunicação em rede.

A aplicação fornece mecanismos de descoberta de usuários, comunicação entre 
peers, troca de mensagens serializadas e integração de extensões por meio de 
plugins. A comunicação de rede utiliza dois protocolos de transporte: UDP, 
empregado principalmente na descoberta e comunicação multicast entre as 
instâncias, e TCP, utilizado para comunicação direta e persistente entre peers.

Dessa forma, novos comportamentos podem ser adicionados por meio de plugins 
sem modificar o núcleo da aplicação.

O projeto foi desenvolvido como suporte às práticas da disciplina de Redes de 
Computadores, servindo também como base para experimentação de aplicações 
distribuídas e desenvolvimento de projetos acadêmicos.

# Arquitetura geral

A aplicação é dividida em três grupos principais:

- **Núcleo da aplicação**: A `MainWindow` atua como ponto central da aplicação, 
  sendo responsável por inicializar e integrar esses componentes durante a 
  execução.
- **Sistema de plugins**: responsável por adicionar funcionalidades sem
  alterar o núcleo da aplicação.
- **Comunicação de rede**: responsável pela troca de mensagens entre
  instâncias da aplicação.

## Inicialização da aplicação

1. A MainWindow é criada.
2. O SocketUDP é inicializado, associa-se à porta configurada e ingressa no 
   grupo multicast.
3. O SocketTCP é inicializado e cria um ServerSocket na porta da aplicação 
   para aceitar conexões TCP.
4. Os mecanismos de comunicação iniciam seus respectivos processos de recepção.
5. O HeartbeatManager é criado e inicia o envio periódico de heartbeats.
6. O painel de usuários online é registrado como listener do HeartbeatManager.
7. O PluginLoader procura e carrega os plugins disponíveis.

# Sistema de comunicação

A comunicação entre as instâncias da aplicação é realizada por meio de dois 
mecanismos complementares:

- **UDP multicast/unicast**, implementado por `SocketUDP`
- **TCP**, implementado por `SocketTCP` e `TcpConnection`

Ambos os mecanismos utilizam mensagens serializadas em `byte[]`, permitindo que 
os plugins permaneçam independentes dos detalhes do protocolo de transporte.

## Comunicação UDP

A classe `SocketUDP` é responsável pela comunicação UDP da aplicação.

Ela utiliza um `MulticastSocket` associado à porta configurada pela aplicação e 
ingressa no grupo multicast `224.0.0.3`. Por ser baseada em UDP, a comunicação não 
estabelece uma conexão permanente entre os peers.

A classe estende `Thread`, mantendo um processo contínuo de recepção de datagramas 
enquanto a aplicação está em execução.

O SocketUDP suporta dois modos de envio:

- **multicast**, destinado ao grupo de peers
- **unicast**, destinado diretamente ao endereço IP de um peer específico

### Inicialização

Durante sua inicialização, o `SocketUDP`:

1. obtém o endereço do grupo multicast;
2. cria um `MulticastSocket` utilizando a porta configurada na `MainWindow`;
3. configura os buffers de envio e recepção;
4. habilita o reuso do endereço;
5. ingressa no grupo multicast.

O tamanho dos buffers de comunicação é configurado para `256000` bytes.

### Envio de mensagens UDP

O `SocketUDP` suporta dois modos de envio de mensagens: multicast e unicast, ambos 
baseados no mesmo mecanismo interno de criação e envio de DatagramPacket.

O método `sendMulticast()` envia uma mensagem para o grupo multicast da aplicação. 
Nesse caso, o `DatagramPacket` é direcionado ao endereço multicast configurado, 
permitindo que todas as instâncias participantes do grupo recebam a mensagem 
simultaneamente.

Já o método `sendUnicast()` permite enviar uma mensagem diretamente para um peer 
específico. Nesse caso, o `DatagramPacket` é direcionado ao endereço IP informado pelo 
chamador, mantendo a mesma porta utilizada pela aplicação.

Ambos os métodos são, na prática, abstrações de um único mecanismo interno de envio, 
que utiliza o mesmo processo de criação e envio de `DatagramPacket` por baixo dos 
panos. A diferença entre eles está apenas no endereço de destino: enquanto o multicast 
utiliza o grupo compartilhado, o unicast direciona o pacote a um IP específico.

### Recepção de mensagens

O método `receive()` mantém um loop aguardando novos datagramas.

Ao receber uma mensagem, o `SocketUDP` inicialmente tenta identificar se os dados 
correspondem a uma `HeartbeatMessage`.

O processamento segue o seguinte fluxo:

1. O datagrama é recebido pelo `MulticastSocket`.
2. Os dados recebidos são analisados pelo método `tryParseHeartbeat()`.
3. Caso seja identificada uma `HeartbeatMessage`, ela é encaminhada ao 
   `HeartbeatManager`, juntamente com o endereço IP de origem.
4. Caso não seja um heartbeat, a mensagem é encaminhada para todos os plugins 
   carregados.
5. Cada plugin decide se a mensagem recebida pertence à sua funcionalidade.

Essa separação mantém as mensagens de infraestrutura, como heartbeat, sob 
responsabilidade do núcleo da aplicação, enquanto as mensagens específicas das 
funcionalidades são processadas pelos plugins.

### Identificação de mensagens de heartbeat

O método `tryParseHeartbeat()` tenta desserializar os dados recebidos e verificar se o 
objeto resultante é uma instância de `HeartbeatMessage`.

Caso a mensagem não possa ser desserializada ou não corresponda ao tipo esperado, o 
método retorna `null`. Nesse caso, a mensagem é tratada como uma mensagem destinada aos
plugins.

### Encerramento

O método `close()` interrompe a execução do processo de recepção, remove a aplicação 
do grupo multicast e fecha o `MulticastSocket`.

A variável `running` é utilizada para sinalizar que o processo de recepção deve ser
encerrado.

## Comunicação TCP

A comunicação TCP é implementada pela classe `SocketTCP`.

Diferentemente do UDP, o TCP estabelece conexões entre dois peers. Essas conexões 
são mantidas enquanto forem necessárias e podem ser reutilizadas para o envio de 
múltiplas mensagens.

A arquitetura utiliza uma conexão TCP persistente por endereço de peer.

O SocketTCP possui um mapa de conexões:

`InetAddress` -> `TcpConnection`

Esse mapa permite localizar rapidamente uma conexão existente para determinado peer e 
reutilizá-la durante novos envios.

### SocketTCP

A classe `SocketTCP` estende `Thread` e implementa `PeerListener`.

Ao ser criada, ela inicializa um `ServerSocket` utilizando a porta configurada na 
aplicação.

O servidor TCP permanece aguardando novas conexões enquanto a aplicação estiver 
em execução.

### Envio de mensagens TCP

O método `send()` recebe uma mensagem serializada e o endereço IP do peer destinatário.

Antes de enviar a mensagem, o `SocketTCP` verifica se já existe uma conexão válida com 
o peer.

O comportamento é:

1. Procurar uma conexão existente no mapa de conexões.
2. Verificar se a conexão está fechada.
3. Caso não exista uma conexão válida, criar um novo Socket TCP para o endereço do peer.
4. Encapsular o socket em uma `TcpConnection`.
5. Registrar a conexão no mapa.
6. Iniciar uma tarefa de recepção para essa conexão.
7. Enviar a mensagem pela conexão.

Assim, uma conexão TCP existente pode ser reutilizada para várias mensagens, evitando
a criação de um novo socket para cada envio.

### Recepção de conexões

O método `receive()` permanece aguardando novas conexões através de 
`ServerSocket.accept()`.

Quando uma conexão é aceita:

O endereço IP do peer é obtido.
Um objeto `TcpConnection` é criado para encapsular o socket.
A conexão é registrada no mapa de conexões.
Caso já exista uma conexão associada ao mesmo endereço, a conexão anterior é encerrada.
Uma tarefa independente de recepção é criada para a nova conexão.

Esse modelo permite que o servidor aceite múltiplas conexões sem bloquear o 
processamento das conexões já existentes.

### Recepção concorrente

A recepção das mensagens TCP é realizada de maneira concorrente por meio de um
`ExecutorService`.

Cada conexão possui uma tarefa própria responsável por receber continuamente as 
mensagens.

O fluxo de recepção é:

```
SocketTCP
|
+-- Peer A → TcpConnection → tarefa de recepção
|
+-- Peer B → TcpConnection → tarefa de recepção
|
+-- Peer C → TcpConnection → tarefa de recepção
```

Dessa forma, uma conexão bloqueada ou aguardando dados não impede que outras conexões
continuem sendo processadas.

Quando uma mensagem é recebida por uma `TcpConnection`, ela é encaminhada para todos os
plugins registrados na `MainWindow`.

O plugin responsável pela funcionalidade deve identificar o tipo da mensagem e
realizar o processamento correspondente.

### TcpConnection

A classe TcpConnection encapsula uma conexão TCP individual entre dois peers.

Ela mantém:

o Socket utilizado na conexão;
um DataInputStream para recepção;
um DataOutputStream para envio.

Além de encapsular esses recursos, a classe é responsável pelo framing das mensagens.

### Framing das mensagens TCP

A classe `TcpConnection` encapsula uma conexão TCP individual entre dois peers.

Ela mantém:

1. o `Socket` utilizado na conexão;
2. um `DataInputStream` para recepção;
3. um `DataOutputStream` para envio.

Além de encapsular esses recursos, a classe é responsável pelo framing das mensagens.

Framing das mensagens TCP

O protocolo TCP fornece um fluxo contínuo de bytes e não preserva os limites das 
mensagens enviadas pela aplicação.

Por esse motivo, é adicionado o tamanho da mensagem antes do conteúdo.

O formato utilizado é:

```
+----------------------+----------------------+
| tamanho da mensagem | dados da mensagem    |
+----------------------+----------------------+
4 bytes              N bytes
```

O tamanho é enviado como um `int`, seguido pelos bytes da mensagem.

No envio, o método `TcpConnection.send()`:

1. obtém o tamanho do vetor de bytes;
2. escreve o tamanho no `DataOutputStream`;
3. escreve os dados da mensagem;
4. realiza `flush()` no stream.

O acesso ao stream de saída é sincronizado para evitar que envios concorrentes 
misturem seus dados.

Na recepção, o método `receive()`:

1. lê o tamanho da mensagem;
2. verifica se o tamanho é válido;
3. lê a quantidade correspondente de bytes;
4. retorna o vetor de bytes completo.

Esse mecanismo permite que várias mensagens sejam transmitidas pela mesma conexão
TCP sem que o receptor perca a delimitação entre elas.

### Integração entre TCP e gerenciamento de peers

O `SocketTCP` implementa a interface PeerListener para acompanhar as alterações na 
lista de peers ativos.

O `HeartbeatManager` mantém a lista de peers atualmente detectados na rede. Quando 
essa lista é alterada, o SocketTCP recebe a notificação por meio do método
`onPeersChanged()`.

O método obtém os endereços IP dos peers atualmente ativos e compara esses endereços
com as conexões TCP existentes.

Quando uma conexão pertence a um peer que não está mais ativo:

a conexão é removida do mapa;
a conexão TCP é encerrada;
os recursos associados são liberados.

Esse mecanismo impede que conexões TCP permaneçam abertas indefinidamente para peers
que já deixaram de participar da rede.

O fluxo de integração pode ser representado da seguinte forma:
```
HeartbeatManager
|
| peers ativos alterados
|
v
SocketTCP.onPeersChanged()
|
| compara peers ativos
|
v
+---- peer ativo ------> mantém conexão
|
+---- peer inativo ----> fecha conexão
```

### Message

A classe `Message` representa a estrutura base das mensagens trocadas pela aplicação.

Todas as mensagens utilizadas pelo sistema devem herdar dessa classe. Ela implementa
`Serializable`, permitindo que objetos de mensagem sejam convertidos em vetores de 
bytes para transmissão pela rede.

A serialização é realizada pelo método `toByteArray()`, que transforma uma instância 
da mensagem em uma representação binária enviada pelos mecanismos de comunicação.

As mensagens específicas devem estender essa classe, adicionando os atributos e 
comportamentos necessários para cada funcionalidade.

A camada de transporte não depende do conteúdo específico das mensagens. Tanto o 
UDP quanto o TCP recebem mensagens serializadas como `byte[]`.

### Heartbeat

O mecanismo de heartbeat é utilizado para identificar quais usuários estão ativos 
na rede.

A aplicação envia periodicamente uma `HeartbeatMessage` contendo informações do usuário,
um identificador único da instância da aplicação e um timestamp.

Essas mensagens são transmitidas utilizando a comunicação UDP multicast.

Quando outra instância recebe uma mensagem de heartbeat, o `SocketUDP` identifica 
o tipo da mensagem e encaminha o objeto ao `HeartbeatManager`, juntamente com o endereço
IP de origem.

O `HeartbeatManager` registra ou atualiza o peer correspondente, utilizando o 
identificador da instância para diferenciá-lo das demais.

Um peer é considerado inativo quando permanece mais de 15 segundos sem receber um novo 
heartbeat. O `HeartbeatManager` então remove o peer da lista e notifica todos os 
componentes registrados como listeners de mudanças de peers.

Esses listeners podem incluir diferentes partes da aplicação, como componentes 
da interface gráfica, mecanismos de comunicação (como o `SocketTCP`) e também plugins 
que desejem reagir à presença ou ausência de usuários na rede.

Dessa forma, o sistema permite que tanto o núcleo da aplicação quanto extensões 
externas sejam notificados sobre alterações no estado da rede, mantendo a arquitetura 
flexível e extensível.

O fluxo completo é:

```
HeartbeatManager 
| 
| cria HeartbeatMessage 
|
v 
Message.toByteArray() 
| 
v 
SocketUDP.sendMulticast() 
| 
v 
UDP Multicast 
| 
v 
SocketUDP.receive() 
| 
v 
HeartbeatManager.receiveHeartbeat() 
| 
v 
Lista de peers ativos 
| 
+----> Interface de usuários online 
| 
+----> SocketTCP (PeerListener) 
| 
+----> Plugins (PeerListener)
```

## Sistema de plugins

O sistema de plugins é responsável por permitir a extensão das funcionalidades da 
aplicação de forma modular, sem a necessidade de alterações no núcleo do sistema. 
Ele define um contrato comum que deve ser seguido por todas as extensões, garantindo 
que possam ser carregadas dinamicamente e integradas à comunicação e à interface da 
aplicação.

### Interface Plugin

A interface `Plugin` define o contrato que deve ser implementado por qualquer
extensão da aplicação.

Um plugin deve fornecer informações básicas de identificação (`name`,
`author` e `version`), inicializar seu estado através de `createPlugin()` e
participar do sistema de comunicação utilizando mensagens derivadas de
`Message`.

Os principais métodos são:

#### Identificação

Os métodos:
- `getName()`
- `getAuthor()`
- `getVersion()`

fornecem informações utilizadas pela aplicação para identificar o plugin.

#### Inicialização

`createPlugin(MainWindow window)`

É chamado após o carregamento do plugin e permite que ele registre seus
componentes na aplicação, como menus ou elementos de interface.

#### Comunicação

`getMessage()`

Deve retornar uma mensagem utilizada pelo plugin para comunicação com outras
instâncias da aplicação.

`receiveMessage(byte[] message)`

É chamado pela aplicação sempre que uma mensagem é recebida. Cada plugin deve
verificar se a mensagem pertence ao seu tipo e realizar o processamento
correspondente.

### Descoberta de plugins

Para serem reconhecidos, os plugins devem possuir os arquivos de configuração
do Java Service Provider Interface (SPI):

```
MeusPlugins
|
+---META-INF
    |
    +---services
        |
        +---pitiupi.models.Plugin
```

O arquivo `pitiupi.models.Plugin` deve conter o nome completo das classes que
implementam a interface Plugin:

```
MeusPlugins.plugins.NomeDaClasse
```

Para serem carregados, os plugins devem estar empacotados como arquivos JAR
e colocados no diretório plugins da aplicação.

### Carregamento de plugins

Ao iniciar a aplicação, o `PluginLoader` executa os seguintes passos:

1. Procura arquivos `.jar` dentro do diretório `plugins`.
2. Cria um `URLClassLoader` contendo os JARs encontrados.
3. Utiliza `ServiceLoader` para localizar classes que implementam
   `Plugin`.
4. Instancia cada plugin encontrado.
5. Chama o método `createPlugin(MainWindow)`.
6. Registra o plugin na lista de plugins ativos da aplicação.

## Interface gráfica

A interface gráfica da aplicação é baseada em Swing e é centralizada na
classe `MainWindow`.

Durante a inicialização, cada plugin recebe uma referência para a
`MainWindow` através do método `createPlugin(MainWindow)`.

Essa referência permite integrar componentes gráficos à aplicação, sendo a
principal forma de extensão a adição de menus à barra de menus:

```
JMenu menu = new JMenu("Meu Plugin");
window.addMenu(menu);
```

Visão geral da comunicação

A arquitetura de comunicação pode ser resumida da seguinte maneira:
```
                         +------------------+
                         |    MainWindow    |
                         +--------+---------+
                                  |
             +--------------------+--------------------+
             |                    |                    |
             v                    v                    v
      HeartbeatManager       SocketUDP            SocketTCP
             |                    |                    |
             |                    |                    |
             v                    v                    v
         Heartbeat           UDP Multicast       TCP Connections
                                  |                    |
                                  |                    |
                                  +---------+----------+
                                            |
                                            v
                                         Plugins
                                            |
                                            v
                                        Message
```

O UDP multicast fornece a infraestrutura de descoberta e manutenção da presença dos
peers, além de permitir comunicação multicast e unicast.

O TCP fornece canais diretos e persistentes entre peers, com uma conexão reutilizável 
por endereço.

Os dois mecanismos encaminham as mensagens destinadas às funcionalidades da aplicação 
para os plugins, mantendo separadas as responsabilidades de transporte, infraestrutura
e lógica específica de cada extensão.