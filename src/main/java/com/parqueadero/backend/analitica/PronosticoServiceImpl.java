package com.parqueadero.backend.analitica;

import com.parqueadero.backend.config.PronosticoProperties;
import com.parqueadero.backend.exception.NegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.function.Function;

@Service
public class PronosticoServiceImpl implements PronosticoService {

    private static final Logger log = LoggerFactory.getLogger(PronosticoServiceImpl.class);
    private static final String NO_DISPONIBLE = "El servicio de pronóstico no está disponible. Intente más tarde";

    private final RestClient rest;

    public PronosticoServiceImpl(PronosticoProperties props) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(props.getTimeoutConexion());
        fabrica.setReadTimeout(props.getTimeoutLectura());
        this.rest = RestClient.builder().baseUrl(props.getUrl()).requestFactory(fabrica).build();
    }

    @Override
    public String vacancia(boolean incluirSimulados) {
        return pedir(u -> u.path("/pronostico/vacancia").queryParam("incluirSimulados", incluirSimulados).build());
    }

    @Override
    public String ocupacion(int horizonteDias, boolean incluirSimulados) {
        return pedir(u -> u.path("/pronostico/ocupacion").queryParam("horizonteDias", horizonteDias)
                .queryParam("incluirSimulados", incluirSimulados).build());
    }

    @Override
    public String llegadas(boolean incluirSimulados) {
        return pedir(u -> u.path("/pronostico/llegadas").queryParam("incluirSimulados", incluirSimulados).build());
    }

    private String pedir(Function<org.springframework.web.util.UriBuilder, java.net.URI> uri) {
        try {
            String cuerpo = rest.get().uri(uri).retrieve().body(String.class);
            if (cuerpo == null || cuerpo.isBlank()) {
                throw new IllegalStateException("respuesta vacia");
            }
            return cuerpo;
        } catch (RestClientException | IllegalStateException e) {
            // Solo el tipo de error: ni la URL, ni el cuerpo de la respuesta.
            log.warn("Servicio de pronóstico no disponible ({})", e.getClass().getSimpleName());
            throw new NegocioException(HttpStatus.SERVICE_UNAVAILABLE, "PRONOSTICO_NO_DISPONIBLE", NO_DISPONIBLE);
        }
    }
}
