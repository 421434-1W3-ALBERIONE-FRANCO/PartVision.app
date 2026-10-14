package com.partvision.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.springframework.boot.jackson.JsonComponent;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Las fechas sin zona ({@link LocalDateTime}) estan en UTC: el servidor corre en UTC, Hibernate
 * escribe en UTC ({@code hibernate.jdbc.time_zone}) y la aplicacion arranca en UTC
 * ({@link com.partvision.PartVisionApplication}). Salian al JSON sin zona, y el navegador las
 * tomaba como hora local: en Argentina, tres horas adelantadas. La corrida de ADS de las 6:30
 * figuraba a las 9:30 y el boton tocado a las 2:44 decia 5:44. Con la "Z" al final, el
 * navegador las pasa a la hora de quien mira.
 */
@JsonComponent
public class FechaUtcSerializer extends StdSerializer<LocalDateTime> {

    public FechaUtcSerializer() {
        super(LocalDateTime.class);
    }

    @Override
    public void serialize(LocalDateTime fecha, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(fecha.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
    }
}
