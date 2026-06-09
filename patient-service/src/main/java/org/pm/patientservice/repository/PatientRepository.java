package org.pm.patientservice.repository;


import org.pm.patientservice.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;


@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    boolean existsByEmail(String email);
    // whether there is another patient with the same email as the one trying to update but with
    // different ID (so we can update the current one)
    boolean existsByEmailAndIdNot(String email, UUID id);

}
