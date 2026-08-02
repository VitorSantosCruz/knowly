package br.com.conectabyte.knowly.tenancy.exception;

/**
 * tenant-crud REQ-2: a present-but-blank mandatory field (any of {@code tenant-creation} REQ-2's
 * mandatory fields except {@code complement}) was submitted on an edit request -- rejected with
 * 400, no partial update applied. Bean Validation can't express this ("blank" must be distinguished
 * from "omitted" on an otherwise-optional field), so this is enforced service-side.
 */
public class InvalidTenantEditException extends RuntimeException {

    public InvalidTenantEditException(String message) {
        super(message);
    }
}
