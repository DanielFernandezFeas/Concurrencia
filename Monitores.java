/ControlAlmacenMonitor.java                                                                         0100644 0000000 0000000 00000030552 14451614733 0014455 0                                                                                                    ustar 00                                                                0000000 0000000                                                                                                                                                                        package cc.controlAlmacen;

import es.upm.babel.cclib.Monitor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class ControlAlmacenMonitor implements ControlAlmacen {
  //Inicializa el monitor
  private final Monitor mutex;
  //Inicialiaza la lista sobre la que se guardaran los clientes en espera
  private final List<ClienteEnEspera> clientesEnEsperaCondEntregar =  new ArrayList<>();
  //Inicializa el Mapa formado por el elemento mas la condicion papra guardar las elementos de OfrecerReabastecer
  private final Map<String, Monitor.Cond> condiciones = new ConcurrentHashMap<>();
  //Agrega un elemento + su condicion a la lista de OfrecerReabastecer en espera
  public void agregarHilo(String nombreHilo,Monitor.Cond condicionesHilo) {
    condiciones.put(nombreHilo, condicionesHilo);
  }
  //Elementos que se introducen en cada una de las listas que estan esperando por un monitor.await
  private static class ClienteEnEspera {
    //Inicializacion de los distintos atributos que va a tener el elemento
    private final String itemId;
    private final int cantidad;
    private final long tiempoEspera;
    //Getters y setter necesarios de los atributos para poder trabajar con las listas de clientes en espera
    public String getItemId() {
      return itemId;
    }
    public int getCantidad() {
      return cantidad;
    }
    public long getTiempoEspera() {
      return tiempoEspera;
    }
    //Inicializacion y getter setter del elemento condicion que se utiliza
    private final Monitor.Cond condition;
    public Monitor.Cond getCondition() {
      return condition;
    }
    public void setCondition(Monitor.Cond newCond) {
    }
    //Inicializa el elemento con todos los atributos
    public ClienteEnEspera(String itemId, int cantidad, long tiempoEspera, Monitor.Cond condition) {
      this.itemId = itemId;
      this.cantidad = cantidad;
      this.tiempoEspera = tiempoEspera;
      this.condition = condition;
    }
  }
  static class Producto {
    //Los datos que contiene cada uno de los producto y sobre los que se trabaja
    private int disponibles;
    private int enCamino;
    private int comprados;
    private final int minDisponibles;
    //Getters y setter necesarios de los atributos para poder trabajar con las listas de productos
    public void setDisponibles(int disponibles) {
      this.disponibles = disponibles;
    }
    public void setEnCamino(int enCamino) {
      this.enCamino = enCamino;
    }
    public void setComprados(int comprados) {
      this.comprados = comprados;
    }
    public int getDisponibles() {
      return this.disponibles;
    }
    public int getEnCamino() {
      return this.enCamino;
    }
    public int getComprados() {
      return this.comprados;
    }
    public int getMinDisponibles() {
      return this.minDisponibles;
    }

    //Inicializa todos los datos del producto
    public Producto(int minDisponibles) {
      this.disponibles = 0;
      this.enCamino = 0;
      this.comprados = 0;
      this.minDisponibles = minDisponibles;
    }
  }
  /**
   * Creamos los mapas que se van a autilizar para operar en todas las funciones, un mapa con todos los productos que
   * se iran editando y otro mapa con los que hay en el almacen
   */
  private final Map<String, Integer> productos;
  private final Map<String, Producto> almacen;

  /**
   * Introducimos e incializamos todo lo necesario para poder opera tanto con los productos como con el almacen
   */
  public ControlAlmacenMonitor(Map<String, Integer> tipoProductos) {
    this.productos = tipoProductos;
    this.almacen = new ConcurrentHashMap<>();
    this.mutex = new Monitor();
    //Introduce los datos mediante iteradores
    for (Map.Entry<String, Integer> entry : tipoProductos.entrySet()) {
      String itemId = entry.getKey();
      int minDisponible = entry.getValue();
      Producto producto = new Producto(minDisponible);
      this.almacen.put(itemId, producto);
    }

  }

  /**
   * Funcion comprar en la que si se cumplen las condiones, suma la cantidad de productos que le llega
   * a los productos comprados
   */
  public boolean comprar(String clientId, String itemId, int cantidad) {
    //Se adquiere el mutex para garantizar la exclusion mutua
    mutex.enter();
    try {
      //Comprobamos que la cantidad sea positiva y que el producto este presente en la lista que tenemos
      if (cantidad <= 0 || !this.productos.containsKey(itemId)) {
        throw new IllegalArgumentException("Producto no encontrado"); //Si se cumple lanzamos exception
      }
      //Iniciamos los valores de los atributos del producto que le llega a comprar
      Producto producto = this.almacen.get(itemId);
      int comprados = producto.getComprados();
      int disponibles = producto.getDisponibles();
      int enCamino = producto.getEnCamino();
      boolean result = disponibles + enCamino >= cantidad + comprados ;
      //Si no se cumple la igualda no se puede realizar una compra de un producto
      if (result) {
        //Realizamos las operaciones
        producto.setComprados(comprados + cantidad);
        //Llamamos a la funcion desbloquear pasandole el elemento una vez realizamos la operacion
        desbloquear(itemId);
        return true;
      } else {
        return false;
      }
    }finally {
      //Se libera para permitir el acceso a otros hilos
      mutex.leave();
    }
  }
  /**
   * Funcion entregar en la que si se cumplen las condiones, resta a comprados y disponibles la cantidad indicada
   */
  public void entregar(String clientId, String itemId, int cantidad) {
    mutex.enter();
    try {
      //Comprobamos que la cantidad sea positiva y que el producto este presente en la lista que tenemos
      if (cantidad <= 0 || !this.productos.containsKey(itemId)) {
        throw new IllegalArgumentException("Producto no encontrado");
      }
      //Igualamos los valores
      Producto producto = this.almacen.get(itemId);
      int disponibles = producto.getDisponibles();
      int comprado;

      //No cumple la condicion o la lista de clientes en espera para entregar esta vacia
      if (disponibles < cantidad || clientesEnEsperaCondEntregar.size() != 0) {
        //Creamos un nuevo elelemento cliente en el que introducimos los datos asi como el tiempo
        ClienteEnEspera cliente = new ClienteEnEspera(itemId, cantidad, System.currentTimeMillis(), mutex.newCond());
        //Añadimos a la lista y cambiamos la condicion
        clientesEnEsperaCondEntregar.add(cliente);
        cliente.getCondition().await();
      }
      //Una vez ha sido desbloqueada la condicion hay que actualizar los valores antes de operar con ellos
      producto = this.almacen.get(itemId);
      disponibles = producto.getDisponibles();
      comprado = producto.getComprados();

      //Realizamos las operaciones que corresponden a la funcion de entregar
      producto.setDisponibles(disponibles - cantidad);
      producto.setComprados(comprado - cantidad);

      //Despues de realizar las operaciones llamamos a la funcion desbloquear
      desbloquear(itemId);
    } finally {
      mutex.leave();
    }
  }
  /**
   * Funcion devolver en la que si se cumplen las condiones, suma la cantidad a los productos disponibles
   */
  public void devolver(String clientId, String itemId, int cantidad) {
    mutex.enter();
    try {
      //Comprobamos que la cantidad sea positiva y que el producto este presente en la lista que tenemos
      if (cantidad <= 0 || !this.productos.containsKey(itemId)) {
        throw new IllegalArgumentException("Producto no encontrado");
      }
      //Iniciamos lo valores de los atributos que llegan a la funcion
      Producto producto = this.almacen.get(itemId);
      int disponibles = producto.getDisponibles();

      //Realizamos las operaciones que corresponden a la funcion de devolver
      producto.setDisponibles(disponibles + cantidad);

      //Despues de realizar las operaciones llamamos a la funcion desbloquear
      desbloquear(itemId);
    } finally {
      mutex.leave();
    }
  }
  /**
   * Funcion OfrecerReabastecer en la que si se cumplen las condiones, suma a enCamino la cantidad de elementos
   */
  public void ofrecerReabastecer(String itemId, int cantidad) {
    mutex.enter();
    try {
      //Comprobamos que la cantidad sea positiva y que el producto este presente en la lista que tenemos
      if (cantidad <= 0 || !this.productos.containsKey(itemId)) {
        throw new IllegalArgumentException("Producto no encontrado");
      }
      //Iniciamos lo valores de los atributos que llegan a la funcion
      Producto producto = this.almacen.get(itemId);
      int disponibles = producto.getDisponibles();
      int enCamino = producto.getEnCamino();
      int comprados = producto.getComprados();
      int minDisp = producto.getMinDisponibles();

      //No cumple la condicion o la lista de productos esperando para ser reabastecidos
      if (disponibles + enCamino - comprados >= minDisp) {
        agregarHilo(itemId, mutex.newCond());
        condiciones.get(itemId).await();
      }

      //Una vez ha sido desbloqueada la condicion hay que actualizar los valores antes de operar con ellos
      producto = this.almacen.get(itemId);
      int enCamino1 = producto.getEnCamino();
      //Realizamos las operaciones que corresponden a la funcion de OfrecerReabastecer
      producto.setEnCamino(enCamino1 + cantidad);
      //Despues de realizar las operaciones llamamos a la funcion desbloquear
      desbloquear(itemId);
    } finally {

      mutex.leave();
    }
  }
  /**
   * Funcion comprar en la que si se cumplen las condiones, resta la cantidad a enCamino y se la suma a disponibles
   */
  public void reabastecer(String itemId, int cantidad) {
    mutex.enter();
    try {
      //Comprobamos que la cantidad sea positiva y que el producto este presente en la lista que tenemos
      if (cantidad <= 0 || !this.productos.containsKey(itemId)) {
        throw new IllegalArgumentException("Producto no encontrado");
      }
      //Iniciamos lo valores de los atributos que llegan a la funcion
      Producto producto = this.almacen.get(itemId);
      int disponibles = producto.getDisponibles();
      int enCamino = producto.getEnCamino();
      //Realizamos las operaciones que corresponden a la funcion de Reabastecer
      producto.setDisponibles(disponibles + cantidad);
      producto.setEnCamino(enCamino - cantidad);
      //Despues de realizar las operaciones llamamos a la funcion desbloquear
      desbloquear(itemId);
    }finally {
      mutex.leave();
    }
  }
  private void desbloquear( String itemId) {
    mutex.enter();
    try {
      //Iniciamos lo valores de los atributos que llegan a la funcion
      Producto producto = this.almacen.get(itemId);
      int disponibles = producto.getDisponibles();
      int enCamino = producto.getEnCamino();
      int comprados = producto.getComprados();
      int minDisponibles = producto.getMinDisponibles();
      //Inicia el valor de la entrega que se va a borrar de la lista de espera
      ClienteEnEspera entregaDesbloqueo = null;
      //Guardamos el tiempo maximo permitido para calcular el que lleva mas tiempo
      long entregaDesbloqueoTiempo = Long.MAX_VALUE;

      //Si no esta vacia
      if (!clientesEnEsperaCondEntregar.isEmpty()) {
        //Se recorre la lista
        for (ClienteEnEspera entrega : clientesEnEsperaCondEntregar) {
          //Comprueba que sean el mismo elemento
          if (entrega.getItemId().equals(itemId)) {
            //Comparamos tiempo
            if (entrega.getTiempoEspera() < entregaDesbloqueoTiempo) {
              //Marca el elemento que mas tiempo lleva
              entregaDesbloqueo = entrega;
              entregaDesbloqueoTiempo = entrega.getTiempoEspera();
            }
          }
        }
      }
      //Si existe y cumple las condiciones
      if (entregaDesbloqueo != null && disponibles >= entregaDesbloqueo.getCantidad() ) {
        //Se borra y se manda la señal al hilo en espera
        clientesEnEsperaCondEntregar.remove(entregaDesbloqueo);
        entregaDesbloqueo.getCondition().signal();
      }
      //Condicion para que se realice la operacion
      if (disponibles + enCamino - comprados < minDisponibles ){
        //Recorremos el mapa
        for (Map.Entry<String, Monitor.Cond> entry : condiciones.entrySet()) {
          //Si son iguales
          String nombre = entry.getKey();
          if (nombre.equals(itemId)) {
            //Mandamos la señal
           entry.getValue().signal();
          }
        }
      }
    }finally{
      mutex.leave();
    }
  }
}                                                                                                                                                      /logs/test.log                                                                                      0100644 0000000 0000000 00000001067 14451615323 0011762 0                                                                                                    ustar 00                                                                0000000 0000000
Thanks for using JUnit! Support its development at https://junit.org/sponsoring


Test run finished after 36740 ms
[         3 containers found      ]
[         0 containers skipped    ]
[         3 containers started    ]
[         0 containers aborted    ]
[         3 containers successful ]
[         0 containers failed     ]
[        45 tests found           ]
[         0 tests skipped         ]
[        45 tests started         ]
[         0 tests aborted         ]
[        45 tests successful      ]
[         0 tests failed          ]
