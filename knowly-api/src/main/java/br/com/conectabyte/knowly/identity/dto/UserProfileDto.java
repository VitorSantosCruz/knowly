package br.com.conectabyte.knowly.identity.dto;

public record UserProfileDto(
        Long userId,
        String email,
        String fullName,
        String address,
        String rg,
        String cpf,
        String phone) {

    public static UserProfileDto of(Long userId, String email, ProfileFieldsDto fields) {
        return new UserProfileDto(
                userId,
                email,
                fields.fullName(),
                fields.address(),
                fields.rg(),
                fields.cpf(),
                fields.phone());
    }
}
