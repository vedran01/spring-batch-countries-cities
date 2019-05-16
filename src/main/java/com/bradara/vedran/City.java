package com.bradara.vedran;


import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity(name = "cities")
public class City {
  @Id
  @JsonIgnore
  private int id;
  private String name;
  private float lat;
  private float lng;


  @Transient
  @JsonIgnore
  private String alpha3Code;

}
