/ControlAlmacenCSP.java                                                                             0100644 0000000 0000000 00000034110 14451614614 0013443 0                                                                                                    ustar 00                                                                0000000 0000000                                                                                                                                                                        package cc.controlAlmacen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

// paso de mensajes con JCSP
import org.jcsp.lang.*;

public class ControlAlmacenCSP implements ControlAlmacen, CSProcess {

	// algunas constantes de utilidad
	static final int PRE_KO = -1;
	static final int NOSTOCK = 0;
	static final int STOCKOK = 1;
	static final int SUCCESS = 0;
	static final int ENTREGA_REALIAZADA = 2;
	static final int DEVOLUCION_REALIZADA = 3;
	static final int OFRECER_REABASTECER_REALIZADA = 4;
	static final int REABASTECER_REALIZADA = 5;

	// canales para comunicación con el servidor
	private final Any2OneChannel chComprar = Channel.any2one();
	private final Any2OneChannel chEntregar = Channel.any2one();
	private final Any2OneChannel chDevolver = Channel.any2one();
	private final Any2OneChannel chOfrecerReabastecer = Channel.any2one();
	private final Any2OneChannel chReabastecer = Channel.any2one();

	// Resource state --> server side !!

	// peticiones de comprar
	private static class PetComprar {
		public String productId;
		public int q;
		public One2OneChannel chresp;

		PetComprar(String productId, int q) {
			this.productId = productId;
			this.q = q;
			this.chresp = Channel.one2one();
		}
	}

	// peticiones de entregar
	private static class PetEntregar {
		public String productId;
		public int q;
		public One2OneChannel chresp;

		PetEntregar(String productId, int q) {
			this.productId = productId;
			this.q = q;
			this.chresp = Channel.one2one();
		}
	}

	// peticiones de devolver
	private static class PetDevolver {
		public String productId;
		public int q;
		public One2OneChannel chresp;

		PetDevolver(String productId, int q) {
			this.productId = productId;
			this.q = q;
			this.chresp = Channel.one2one();
		}
	}

	// para aplazar peticiones de ofrecerReabastecer
	private static class PetOfrecerReabastecer {
		public String productId;
		public int q;
		public One2OneChannel chresp;

		PetOfrecerReabastecer(String productId, int q) {
			this.productId = productId;
			this.q = q;
			this.chresp = Channel.one2one();
		}
	}

	// peticiones de reabastecer
	private static class PetReabastecer {
		public String productId;
		public int q;
		public One2OneChannel chresp;

		PetReabastecer(String productId, int q) {
			this.productId = productId;
			this.q = q;
			this.chresp = Channel.one2one();
		}
	}

	// INTERFAZ ALMACEN
	public boolean comprar(String clientId, String itemId, int cantidad) {

		// petición al servidor
		PetComprar pet = new PetComprar(itemId, cantidad);
		chComprar.out().write(pet);

		// recibimos contestación del servidor
		// puede ser una de {PRE_KO, NOSTOCK, STOCKOK}
		int respuesta = (Integer) pet.chresp.in().read();

		// no se cumple PRE:
		if (respuesta == PRE_KO)
			throw new IllegalArgumentException();
		// se cumple PRE:
		return (respuesta == STOCKOK);
	}

	public void entregar(String clientId, String itemId, int cantidad) {
		// Petición al servidor para entregar un producto
		PetEntregar pet = new PetEntregar(itemId, cantidad);
		chEntregar.out().write(pet);

		// Almacenamos la respuesta del servidor
		int respuesta = (Integer) pet.chresp.in().read();

		// Si la respuesta es PRE_KO, lanzamos una excepción IllegalArgumentException
		if (respuesta == PRE_KO)
			throw new IllegalArgumentException("No se cumple la condición PRE");
	}

	public void devolver(String clientId, String itemId, int cantidad) {

		// Petición al servidor para devolver un producto
		PetDevolver pet = new PetDevolver(itemId, cantidad);
		chDevolver.out().write(pet);

		// Almacenamos la respuesta del servidor
		int respuesta = (Integer) pet.chresp.in().read();

		// Si la respuesta es PRE_KO, lanzamos una excepción IllegalArgumentException
		if (respuesta == PRE_KO)
			throw new IllegalArgumentException("No se cumple la condición PRE");
	}

	public void ofrecerReabastecer(String itemId, int cantidad) {

		// Petición al servidor para ofrecerReabastecer un producto
		PetOfrecerReabastecer pet = new PetOfrecerReabastecer(itemId, cantidad);
		chOfrecerReabastecer.out().write(pet);

		// Almacenamos la respuesta del servidor
		int respuesta = (Integer) pet.chresp.in().read();

		// Si la respuesta es PRE_KO, lanzamos una excepción IllegalArgumentException
		if (respuesta == PRE_KO)
			throw new IllegalArgumentException("No se cumple la condición PRE");

	}

	public void reabastecer(String itemId, int cantidad) {

		// Petición al servidor para reabastecere un producto
		PetReabastecer pet = new PetReabastecer(itemId, cantidad);
		chReabastecer.out().write(pet);

		// Almacenamos la respuesta del servidor
		int respuesta = (Integer) pet.chresp.in().read();

		// Si la respuesta es PRE_KO, lanzamos una excepción IllegalArgumentException
		if (respuesta == PRE_KO)
			throw new IllegalArgumentException("No se cumple la condición PRE");
	}

	// atributos de la clase
	Map<String, Integer> tipoProductos; // stock mínimo para cada producto

	public ControlAlmacenCSP(Map<String, Integer> tipoProductos) {
		this.tipoProductos = tipoProductos;
		new ProcessManager(this).start(); // al crearse el servidor también se arranca...
	}

// SERVIDOR
	public void run() {

		// para recepción alternativa condicional
		Guard[] entradas = { chComprar.in(), chEntregar.in(), chDevolver.in(), chOfrecerReabastecer.in(),
				chReabastecer.in() };
		Alternative servicios = new Alternative(entradas);
		// OJO ORDEN!!
		final int COMPRAR = 0;
		final int ENTREGAR = 1;
		final int DEVOLVER = 2;
		final int OFRECER_REABASTECER = 3;
		final int REABASTECER = 4;
		// condiciones de recepción: todas a CIERTO
		boolean pre_ko = false;

		// estado del recurso
		Map<String, Producto> almacen;

		// Estructura de clientes en espera de la operacion entregar
		ArrayList<PetEntregar> clientesEnEsperaEntregar;

		// Estructura de clientes en espera de la operacion ofrecerReabastecer
		ArrayList<PetOfrecerReabastecer> clientesEnEsperaOfrecerReabastercer;

		// inicializaciones
		almacen = new HashMap<String, Producto>();
		// Introduce los datos mediante iteradores
		for (Map.Entry<String, Integer> entry : tipoProductos.entrySet()) {
			String itemId = entry.getKey();
			int minDisponible = entry.getValue();
			Producto producto = new Producto(minDisponible);
			almacen.put(itemId, producto);
		}
		// New lista de clientes en espera de entregar pedidos
		clientesEnEsperaEntregar = new ArrayList<PetEntregar>();
		// New lista de productos en espera de ofrecer reabastecer
		clientesEnEsperaOfrecerReabastercer = new ArrayList<PetOfrecerReabastecer>();

		// bucle de servicio
		while (true) {
			// vars. auxiliares
			// tipo de la última petición atendida
			int choice = -1; // una de {COMPRAR, ENTREGAR, DEVOLVER, OFRECER_REABASTECER, REABASTECER}

			// todas las peticiones incluyen un producto y una cantidad
			Producto item = new Producto(99999);
			String pid = "";
			int cantidad = -1;

			// Para comprobar si se la PRE ha fallado
			pre_ko = false;

			choice = servicios.fairSelect();
			switch (choice) {
			case COMPRAR: // CPRE = Cierto
				PetComprar petC = (PetComprar) chComprar.in().read();
				// comprobar PRE:
				pid = petC.productId;
				item = almacen.get(pid);
				cantidad = petC.q;
				if (cantidad < 1 || item == null) { // PRE_KO
					pre_ko = true;
					petC.chresp.out().write(PRE_KO);
				} else { // PRE_OK
					boolean result = item.disponibles + item.enCamino >= cantidad + item.comprados;
					if (result) { // hay stock suficiente
						item.comprados += cantidad;
						petC.chresp.out().write(STOCKOK);
					} else { // no hay stock suficiente
						petC.chresp.out().write(NOSTOCK);
					}
				}
				break;
			case ENTREGAR:
				PetEntregar petEntregarPedido = (PetEntregar) chEntregar.in().read();
				// Vemos si existe el producto a entregar en el almacen
				item = almacen.get(petEntregarPedido.productId);
				cantidad = petEntregarPedido.q;
				if (cantidad < 1 || item == null) { // PRE_KO, no se cumple la PRE
					pre_ko = true;
					petEntregarPedido.chresp.out().write(PRE_KO);
				}
				// En el caso de que se cumpla PRE
				else {
					// CPRE se trata en diferido
					clientesEnEsperaEntregar.add(petEntregarPedido);
				}

				break;
			case DEVOLVER:
				// Vemos si existe el producto a entregar en el almacen
				PetDevolver petDevolverPedido = (PetDevolver) chDevolver.in().read();
				item = almacen.get(petDevolverPedido.productId);
				cantidad = petDevolverPedido.q;
				if (cantidad < 1 || item == null) { // PRE_KO, no se cumple la PRE
					pre_ko = true;
					petDevolverPedido.chresp.out().write(PRE_KO);
				} else {
					// En el caso de que se cumple CPRE se procede con POST actualizamos
					// producto en almacen
					item.setDisponibles(item.getDisponibles() + cantidad);
					almacen.put(petDevolverPedido.productId, item);
					petDevolverPedido.chresp.out().write(DEVOLUCION_REALIZADA);
				}
				break;
			case OFRECER_REABASTECER:
				// Vemos si existe el producto a entregar en el almacen
				PetOfrecerReabastecer petOfrecerReabastecerPedido = (PetOfrecerReabastecer) chOfrecerReabastecer.in()
						.read();
				item = almacen.get(petOfrecerReabastecerPedido.productId);
				cantidad = petOfrecerReabastecerPedido.q;
				if (cantidad < 1 || item == null) { // PRE_KO, no se cumple la PRE
					pre_ko = true;
					petOfrecerReabastecerPedido.chresp.out().write(PRE_KO);
				} else {
					// En el caso de que se cumpla PRE, comprobamos CPRE
					if (item.getDisponibles() + item.getEnCamino() - item.getComprados() >= item.getMinDisponibles()) { // CPRE_KO
						clientesEnEsperaOfrecerReabastercer.add(petOfrecerReabastecerPedido);
					} else {
						// CPRE_OK
						item.setEnCamino(cantidad + item.getEnCamino());
						almacen.put(petOfrecerReabastecerPedido.productId, item);
						petOfrecerReabastecerPedido.chresp.out().write(OFRECER_REABASTECER_REALIZADA);
					}
				}
				break;
			case REABASTECER:
				// Vemos si existe el producto a entregar en el almacen
				PetReabastecer petReabastecerPedido = (PetReabastecer) chReabastecer.in().read();
				item = almacen.get(petReabastecerPedido.productId);
				cantidad = petReabastecerPedido.q;
				if (cantidad < 1 || item == null) { // PRE_KO, no se cumple la PRE
					petReabastecerPedido.chresp.out().write(PRE_KO);
					pre_ko = true;
				} else {
					// En el caso de que se cumple CPRE se procede con POST actualizamos
					// producto en almacen
					item.setDisponibles(item.getDisponibles() + cantidad);
					item.setEnCamino(item.getEnCamino() - cantidad);
					almacen.put(petReabastecerPedido.productId, item);
					petReabastecerPedido.chresp.out().write(REABASTECER_REALIZADA);
				}
				break;
			} // switch

			// tratamiento de peticiones aplazadas

			// Si no se ha quebrantado ninguna pre ni cpre ni se ha ejecutado
			// ofrecer_reabastecer
			// podemos proceder a realizar desbloqueos
			if (!pre_ko && choice != OFRECER_REABASTECER) {
				// peticiones de entregar
				Iterator<PetEntregar> var3 = clientesEnEsperaEntregar.iterator();
				ArrayList<String> productIdEspera = new ArrayList<>();
				// Ids de los productos en espera
				for (PetEntregar p : clientesEnEsperaEntregar) {
					productIdEspera.add(p.productId);
				}
				// Ver la posicion del producto en la lista de espera
				int i = 0;
				// Leemos las peticiones mediante iteradores
				while (var3.hasNext()) {
					// Buscamos el producto en el almacen
					PetEntregar pet = var3.next();
					item = almacen.get(pet.productId);
					cantidad = pet.q;
					// Comprobamos CPRE de entregar
					if (item.disponibles >= cantidad && !productIdEspera.subList(0, i).contains(pet.productId)) {
						// En el caso de que se cumple CPRE se procede con POST actualizamos
						// producto en almacen
						item.setComprados(item.getComprados() - cantidad);
						item.setDisponibles(item.getDisponibles() - cantidad);
						almacen.put(pet.productId, item);
						pet.chresp.out().write(ENTREGA_REALIAZADA);
						// Eliminamos con el iterador la peticion
						var3.remove();
						// Eliminamos productid de la lista espera
						productIdEspera.remove(i);
						// Si se empieza de zero i aqui tiene que ser -1 porque al salir del if
						// incrementa el contador
						i = -1;
						// Volvemos a leer las peticiones mediante iteradores
						var3 = clientesEnEsperaEntregar.iterator();
					}
					i++;
				}
				// peticiones de ofrecer reabastecer
				Iterator<PetOfrecerReabastecer> var4 = clientesEnEsperaOfrecerReabastercer.iterator();
				// Leemos las peticiones mediante iteradores
				while (var4.hasNext()) {
					// Buscamos el producto en el almacen
					PetOfrecerReabastecer pet2 = var4.next();
					item = almacen.get(pet2.productId);
					cantidad = pet2.q;
					// Comprobamos CPRE de ofrecer reabastecer
					if (item.getDisponibles() + item.getEnCamino() - item.getComprados() < item.getMinDisponibles()) {
						// En el caso de que se cumple CPRE se procede con POST actualizamos
						// producto en almacen
						item.setEnCamino(cantidad + item.getEnCamino());
						almacen.put(pet2.productId, item);
						pet2.chresp.out().write(OFRECER_REABASTECER_REALIZADA);
						var4.remove();
					}

				}
			}

		} // bucle servicio
	} // run SERVER

	static class Producto {
		// Los datos que contiene cada uno de los producto y sobre los que se trabaja
		private int disponibles;
		private int enCamino;
		private int comprados;
		private int minDisponibles;

		// Sobrescribe el numero de productos disponibles
		public void setDisponibles(int disponibles) {
			this.disponibles = disponibles;
		}

		public void setEnCamino(int enCamino) {
			this.enCamino = enCamino;
		}

		public void setComprados(int comprados) {
			this.comprados = comprados;
		}

		public void setMinDisponibles(int minDisponibles) {
			this.minDisponibles = minDisponibles;
		}

		// Devuelve el numero de productos que estan disponibles
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

		// Inicializa todos los datos del producto
		public Producto(int minDisponibles) {
			this.disponibles = 0;
			this.enCamino = 0;
			this.comprados = 0;
			this.minDisponibles = minDisponibles;
		}

	}

}
                                                                                                                                                                                                                                                                                                                                                                                                                                                        /logs/test.log                                                                                      0100644 0000000 0000000 00000001067 14451614765 0011773 0                                                                                                    ustar 00                                                                0000000 0000000
Thanks for using JUnit! Support its development at https://junit.org/sponsoring


Test run finished after 36734 ms
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

                                 
