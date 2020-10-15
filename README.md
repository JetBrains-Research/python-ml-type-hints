# python-ml-type-hints

## DeepTyper

Work and training time:  
  Work time -- less than 2 seconds for average sized files (taken from the article, not tested)
  Training time -- not mentioned in the article

Gist -- two bi-RNN connetcted by so called "consistency layers" considering a possibility that identifier appears several times within one code snippet (although it does not consider that it could be appearence of different identifiers). It is done with averaging of output of RNN for each appearence of each identifer within one code snippet. 
Noticed problems:  
  - Model shows weak improvement (for top-1 and top-5 accuracy) against plain RNN according to results -- top-1 accuracy is 2% higher, whereas top-5 accuracy is the same.  
  - Problems connected with previous -- there is a comparison of advanced model with other type inference tools, but the article lacks comparison of plain RNN with such tools (or comparison of advanced RNN with advanced model)   
  - No structural information is considered, code is comprehended as sequence of tokens. This design decision is a trade-off between reusage of existing model and productivity.
  - Closed-world type suggestion offers no opportunity to add new types to model without retraining it

Data:  
  - Top-1000 most starred GitHub projects written mostly in TypeScript, excluding projects with more than 5000 tokens and projects, containing only TypeScript header files, only 776 projects left. Each project wath parsed with ```tsc``` compiler, which infers type information for each identifier (possibly ```any```).
  
Comparison:  
  - Baseline -- naive method, which assigns each identifier the type distribution it has at a training time.
  - Plain RNN -- RNN without consistency layers, shows almost same results, has problems with consistency -- it is possible that plain RNN offers different types for different occurences of one identifier
  - tsc+CheckJS -- comparison against types that ```tsc``` could infer when also equipped with static type-inference tool CheckJS. Hybrid model, which applies DeepTyper to the results of type inference conducted by tsc+CheckJS. DeepTyper shows significant accuracy boost against CheckJS and hybrid model.
  - JSNice -- tool for type-inference in JS functions using statistic from dependency graphsl learned on a large corpus. Since it is only available to use via a web form, only 30 experiments were held (30 random functions were taken from top-100 most starred JS projects on GitHub, for each function correct types were manually determined). In a same manner with CheckJS hybrid model was invented. With confidence threshold set at 90% accuracy reaches 65%, whereas there are only 2% incorrect or partially correct results, (for 167 identifiers, which needed type-inference). It is 10% more, compared to JSNice and DeepTyper с with 0% threshold, whereas it shows a same amount or even less incorrect results.
  

Main pros of DeepTyper -- consistency, indifference to structural information about source code (both pro and flaw -- DeepTyper can be applied to many languages but it does not take in account properties of a specific language)
