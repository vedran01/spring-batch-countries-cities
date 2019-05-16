package com.bradara.vedran;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.persistence.*;
import java.util.Set;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Document
@Entity(name = "countries")
public class Country {
  @Id
  @JsonIgnore
  private int id;

  private String name;

  @Column(name = "alpha2code")
  private String alpha2Code;

  @Column(name = "alpha3code")
  private String alpha3Code;

  @Column(name = "calling_code")
  private String callingCode;

  @Transient
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private String[] callingCodes;

  @OneToMany(fetch = FetchType.EAGER)
  @JoinColumn(name = "country_id")
  private Set<City> cities;
}
