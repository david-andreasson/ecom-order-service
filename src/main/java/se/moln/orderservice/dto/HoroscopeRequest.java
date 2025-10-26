package se.moln.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class HoroscopeRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String gender;

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "birthDate must be YYYY-MM-DD")
    private String birthDate;

    @NotBlank
    private String birthPlace;

    // Optional HH:mm
    @Pattern(regexp = "^$|^\\d{2}:\\d{2}$", message = "birthTime must be HH:mm")
    private String birthTime;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getBirthDate() { return birthDate; }
    public void setBirthDate(String birthDate) { this.birthDate = birthDate; }

    public String getBirthPlace() { return birthPlace; }
    public void setBirthPlace(String birthPlace) { this.birthPlace = birthPlace; }

    public String getBirthTime() { return birthTime; }
    public void setBirthTime(String birthTime) { this.birthTime = birthTime; }
}
