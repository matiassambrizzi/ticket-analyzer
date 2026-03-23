# Frontend

## Estilo de código

- Usar arrow functions (`const fn = () => {}`) en vez del keyword `function`.
- Preferir funciones puras. Las operaciones asíncronas deben retornar `Async<T>` (`() => Promise<T>`) en vez de ejecutarse directamente, postergando el efecto hasta que el llamador lo invoque explícitamente.
