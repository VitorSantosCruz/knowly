package br.com.conectabyte.knowly.identity.dto;

/** REQ-2: a structured, single current address. */
public record AddressDto(
        String cep,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String estado,
        String pais) {}
