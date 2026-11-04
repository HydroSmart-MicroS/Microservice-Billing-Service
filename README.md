# Billing

Planes Premium S/40/1 y Black S/120/4, vigencia por cuenta, órdenes de consumo y Checkout externo verificable. No necesita claves Stripe locales.

DDD: application/internal, domain/model y domain/services, infrastructure, interfaces/acl e interfaces/rest.
La base es exclusiva de este contexto. Consultas de configuración y colaboración entre servicios usan RabbitMQ RPC;
los cambios pasan por outbox y los consumidores confirman después de procesar.

El Dockerfile compila y ejecuta sus pruebas. Iniciar desde la raíz de orquestación con .env privado.

[Funcionalidad y arranque](../README.md) · [Guía RabbitMQ](../docs/rabbitmq-guide.md) · [Contratos](../docs/contracts.md).

## Aviso de agua pendiente

Cada orden de consumo que queda pendiente produce un aviso con mes e importe en
la campanita/bandeja y, si está habilitado, por correo Brevo. Se usa
billing.water.payment.pending.v1 → notifications.alerts → notifications.email.
Se respeta inAppEnabled/emailEnabled; no depende de los avisos de reportes.
El aviso se genera una vez por orden, no al calcular consumo ni en cada reintento.
La actualización incluye pendientes existentes; pagados/anulados quedan excluidos.
El estado actual se consulta en Plan y pagos → Historial de pagos.

## Vencimiento de Checkout

Antes de reutilizar un Checkout pendiente se verifica con el proveedor.
Una expiración confirmada libera el pendiente de plan para generar una orden
nueva; una confirmación paid devuelve esa orden y activa acceso sin duplicarla.
Un error del proveedor o una prueba inválida impiden reutilizar/reemplazar el
enlace. El frontend verifica también al pulsar Continuar pago y permite
Generar nuevo enlace desde los planes fallidos. No se necesitan claves Stripe
adicionales ni cambios en la API de pagos alojada.
