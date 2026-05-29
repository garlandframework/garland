package org.modulartestorchestrator.mapstruct.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "addresses")
public class AddressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "street_name")
    private String streetName;

    @Column(name = "city_name")
    private String cityName;

    @Column(name = "country_code")
    private String countryCode;

    @Column(name = "postal_code")
    private String postalCode;

    public AddressEntity() {}

    public Long getId()             { return id; }
    public String getStreetName()   { return streetName; }
    public String getCityName()     { return cityName; }
    public String getCountryCode()  { return countryCode; }
    public String getPostalCode()   { return postalCode; }

    public void setId(Long id)                  { this.id = id; }
    public void setStreetName(String streetName) { this.streetName = streetName; }
    public void setCityName(String cityName)     { this.cityName = cityName; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    @Override
    public String toString() {
        return "AddressEntity{" +
                "id=" + id +
                ", streetName='" + streetName + '\'' +
                ", cityName='" + cityName + '\'' +
                ", countryCode='" + countryCode + '\'' +
                ", postalCode='" + postalCode + '\'' +
                '}';
    }
}
