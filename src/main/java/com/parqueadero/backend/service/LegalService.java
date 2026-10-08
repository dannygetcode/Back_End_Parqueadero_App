package com.parqueadero.backend.service;

import com.parqueadero.backend.dto.AvisoPrivacidadDTO;

public interface LegalService {

    /** Aviso de privacidad vigente (Ley 1581 de 2012). Su versión es la que la app envía al activar. */
    AvisoPrivacidadDTO avisoPrivacidad();
}
