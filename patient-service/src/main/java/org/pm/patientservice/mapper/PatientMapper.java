package org.pm.patientservice.mapper;


import org.pm.patientservice.dto.PatientRequestDTO;
import org.pm.patientservice.dto.PatientResponseDTO;
import org.pm.patientservice.model.Patient;

import java.time.LocalDate;

public class PatientMapper {

    public static PatientResponseDTO toDTO(Patient patient){
        PatientResponseDTO patientResponseDTO = new PatientResponseDTO();
        patientResponseDTO.setId(patient.getId().toString());
        patientResponseDTO.setEmail(patient.getEmail());
        patientResponseDTO.setName(patient.getName());
        patientResponseDTO.setAddress(patient.getAddress());
        patientResponseDTO.setDateOfBirth(patient.getDateOfBirth().toString());
        return patientResponseDTO;
    }

    public static Patient toPatient(PatientRequestDTO patientRequestDTO){
        Patient newPatient = new Patient();
        newPatient.setName(patientRequestDTO.getName());
        newPatient.setAddress(patientRequestDTO.getAddress());
        newPatient.setEmail(patientRequestDTO.getEmail());
        newPatient.setDateOfBirth(LocalDate.parse(patientRequestDTO.getDateOfBirth()));
        newPatient.setRegisteredDate(LocalDate.parse(patientRequestDTO.getRegisteredDate()));
        return newPatient;
    }

}
