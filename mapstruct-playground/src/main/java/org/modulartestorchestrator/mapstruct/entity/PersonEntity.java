package org.modulartestorchestrator.mapstruct.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "persons")
public class PersonEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first")
    private String first;

    @Column(name = "last")
    private String last;

    @Column(name = "born_at")
    private LocalDate bornAt;

    @Column(name = "identification_card_id")
    private String identificationCardId;

    // Separate table — Hibernate joins on address_id FK in persons table.
    // CascadeType.ALL so persisting a PersonEntity also persists the AddressEntity.
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "address_id")
    private AddressEntity personAddress;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;

    public PersonEntity() {}

    public Long getId()                        { return id; }
    public String getFirst()                   { return first; }
    public String getLast()                    { return last; }
    public LocalDate getBornAt()               { return bornAt; }
    public String getIdentificationCardId()    { return identificationCardId; }
    public AddressEntity getPersonAddress()    { return personAddress; }
    public LocalDateTime getCreatedAt()        { return createdAt; }
    public LocalDateTime getModifiedAt()       { return modifiedAt; }

    public void setId(Long id)                              { this.id = id; }
    public void setFirst(String first)                      { this.first = first; }
    public void setLast(String last)                        { this.last = last; }
    public void setBornAt(LocalDate bornAt)                 { this.bornAt = bornAt; }
    public void setIdentificationCardId(String v)           { this.identificationCardId = v; }
    public void setPersonAddress(AddressEntity personAddress) { this.personAddress = personAddress; }
    public void setCreatedAt(LocalDateTime createdAt)       { this.createdAt = createdAt; }
    public void setModifiedAt(LocalDateTime modifiedAt)     { this.modifiedAt = modifiedAt; }

    @Override
    public String toString() {
        return "PersonEntity{" +
                "id=" + id +
                ", first='" + first + '\'' +
                ", last='" + last + '\'' +
                ", bornAt=" + bornAt +
                ", identificationCardId='" + identificationCardId + '\'' +
                ", personAddress=" + personAddress +
                ", createdAt=" + createdAt +
                ", modifiedAt=" + modifiedAt +
                '}';
    }
}
