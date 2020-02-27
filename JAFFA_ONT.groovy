/***********************************************************
 ** This is the JAFFA pipeline file for fusion detection
 ** without read assembly. Run like so:
 **    bpipe run <path_to_this_file> <path_to_fastq/fasta_files>
 ** See our website for details	on running options:
 ** https://code.google.com/p/jaffa-project/.
 **
 ** Author: Nadia Davidson <nadia.davidson@mcri.edu.au>
 ** Last Update: 2020
 *********************************************************/

codeBase = file(bpipe.Config.config.script).parentFile.absolutePath
load codeBase+"/JAFFA_stages.groovy"

get_fasta = {
   doc "Converting fastqs to fasta"
   output.dir=jaffa_output+branch
   produce(branch+".fasta"){
      exec "$reformat in=$input out=$output threads=$threads ;"
   }
}

minimap2_transcriptome = {
   doc "Aligning candidates to transcriptome using minimap2"
   output.dir=jaffa_output+branch
   produce(branch+".paf"){
        exec """
           time $minimap2 -x map-ont -c $transFasta $input > $output1 ;
        """
   }
}

infer_genome_alignment = {
   doc "Bypassing genomic alignment and infering genome position from transcriptome alignments"
   output.dir=jaffa_output+branch
   produce(branch+"_genome.psl"){
      exec """
       $bypass_genomic_alignment $transTable $input.txt > $output
       """
       }
}

minimap2_genome = {
   doc "Aligning candidates to genome using minimap2"
   output.dir=jaffa_output+branch
   produce(branch+"_genome.paf",branch+"_genome.psl"){
	exec """
	   time $minimap2 -x splice -c $genomeFasta $input > $output1 ;
	   grep \$'\\t+\\t' $output1 | awk -F'\\t' -v OFS="\\t" '{ print \$4-\$3,0,0,0,0,0,0,0,\$5,\$1,\$2,\$3,\$4,\$6,\$7,\$8,\$9,2, 100","\$4-\$3-100",",\$3","\$3+100",",  \$8","\$9-\$4+\$3+100"," }' > $output2 ;
	   grep \$'\\t-\\t' $output1 | awk -F'\\t' -v OFS="\\t" '{ print \$4-\$3,0,0,0,0,0,0,0,\$5,\$1,\$2,\$3,\$4,\$6,\$7,\$8,\$9,2, 100","\$4-\$3-100",", \$2-\$4","\$2-\$4+100",", \$8","\$9-\$4+\$3+100"," }' >> $output2
        """
   }
}


blast_options="-perc_identity=0 -evalue=0.0001"
blat_options="-tileSize=11 -stepSize=7 -minIdentity=0 -minScore=100"
blast_out_fmt="\"6 nident mismatch qseqid qstart qend sseqid qlen evalue\""

readLayout="single"
fastqInputFormat="%.gz"

common_steps = segment { 
   minimap2_transcriptome + //align_reads_to_annotation +
   filter_transcripts +
   extract_fusion_sequences +
   infer_genome_alignment + 
   //minimap2_genome + //align_transcripts_to_genome +
   make_fasta_reads_table +
   get_final_list }


// below is the pipeline for a fasta file
if(args[0].endsWith(fastaSuffix)) {
   run { run_check + fastaInputFormat * [
	    common_steps ] + compile_all_results 
   } //or fastq.gz will be converted to fasta.
} else {
   run { run_check + fastqInputFormat * [
	    get_fasta + common_steps ] + compile_all_results 
   }
}
