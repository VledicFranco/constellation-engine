/**
 * Connection state enum, mirroring Scala's InstanceConnectionState.
 */

export enum ConnectionState {
  Disconnected = "Disconnected",
  Registering = "Registering",
  Active = "Active",
  Draining = "Draining",
  Reconnecting = "Reconnecting",
}
