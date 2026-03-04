package io.quarkus.it.bean.validator;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TestBean {

    @NotBlank
    @Size(max = 100)
    public String name;

    @NotNull
    @Email
    public String email;

    @Min(0)
    @Max(150)
    public int age;

    @Valid
    public Address address;

    @Valid
    public List<PhoneNumber> phoneNumbers;

    public static class Address {
        @NotBlank
        public String street;

        @NotBlank
        public String city;

        @NotNull
        @Size(min = 2, max = 10)
        public String zipCode;
    }

    public static class PhoneNumber {
        @NotBlank
        public String number;
    }
}
