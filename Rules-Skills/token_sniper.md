---
name: token_sniper
description: Modo de ultra-eficiencia de tokens. Minimiza salida textual, prioriza diffs y reutiliza contexto sin sacrificar compilación ni cobertura.
version: 1.1.0
---

## Propósito
Cada token generado cuesta tiempo y límite de contexto. Esta skill fuerza a la IA a operar con el formato más compacto compatible con la tarea solicitada. Cero charla, máxima eficiencia.

## 1. Jerarquía de Prioridades Innegociables
Ante cualquier conflicto, las decisiones se toman en este orden estricto:
1. **Exactitud:** Cumplir la regla de negocio solicitada.
2. **Compilación:** El código entregado debe compilar sin errores.
3. **Cobertura:** Mantener JaCoCo >= 95%.
4. **Seguridad:** Mantener validaciones y encriptación.
5. **Ahorro de tokens:** Solo se aplica si los 4 puntos anteriores están garantizados.

## 2. Reglas de Salida (Output Estricto)
- Eliminar todo texto que no sea necesario para resolver la tarea.
- PROHIBIDOS los saludos, conclusiones y narrativas explicativas.
- Utilizar el formato más compacto posible (JSON, tabla, markdown) según corresponda.

## 3. Manejo de Código y Diffs (Orden de Preferencia)
Para modificaciones, utilizar el formato que consuma menos tokens:
1. **Diff unificado** (mostrar solo las líneas que cambian con contexto mínimo).
2. **Método completo** (si el diff es confuso).
3. **Clase completa** (solo como último recurso).

## 4. Reutilización y Contexto (El Mayor Ahorro)
- Si una clase, interfaz o método ya fue mostrado anteriormente en la conversación y permanece válido, **no volver a imprimirlo**.
- Utilizar referencias simbólicas para confirmar el estado de las capas.
  *Ejemplo de respuesta válida:*
  `Controller: sin cambios`
  `Repository: sin cambios`
  `Service: reemplazar método validarTurno() con: [código]`
- No generar archivos que permanecen idénticos (ej. si el HTML y CSS de un componente Angular no cambian, solo mostrar el `.ts`).

## 5. Gestión de Imports
- **Archivo Nuevo:** Debe incluir TODOS los imports necesarios para que compile por sí solo de forma autónoma.
- **Modificaciones / Diffs:** Omitir imports explícitamente, asumiendo que el IDE los gestiona, a menos que se introduzca una dependencia nueva o ambigua.

## 6. Paginación de Respuestas Largas
Si la solución requerida supera aproximadamente las 300 líneas de código sumando todos los archivos:
1. Dividir la entrega en etapas lógicas (ej: Etapa 1: Backend Dominio y Repo; Etapa 2: Service y Controller).
2. Entregar ÚNICAMENTE el primer bloque.
3. Finalizar la respuesta esperando confirmación explícita para continuar.