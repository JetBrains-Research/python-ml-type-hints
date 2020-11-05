# python-ml-type-hints

## DeepTyper 

Article link: http://vhellendoorn.github.io/PDF/fse2018-j2t.pdf  

### Work and training time
  - Work time &ndash; less than 2 seconds for average sized files (taken from the article, not tested)
  - Training time &ndash; not mentioned in the article

### Gist 
Two bi-RNN connected by so-called "consistency layers" considering a possibility that identifier appears several times 
within one code snippet (although it does not consider that it could be appearance of different identifiers). 
It is done with averaging the output of RNN for each appearance of each identifier within one code snippet. 

### Noticed problems
  - Model shows weak improvement (for top-1 and top-5 accuracy) against plain RNN according to results:
    - Top-1 accuracy is 2% higher
    - Top-5 accuracy stays the same  
  - Problems connected with the previous one &ndash; there is a comparison of the advanced model with other type 
  inference tools, but the article lacks comparison of plain RNN with such tools (or comparison of advanced RNN with 
  advanced model).
  - No structural information considered &ndash; code is comprehended as a sequence of tokens. This design decision is 
  a trade-off between reusability of an existing model (i.e., the model can be transferred to arbitrary languages) and 
  quality of predictions.
  - Closed-world type suggestion offers no opportunity to add new types to model without retraining it from scratch.

### Data
  - Top-1000 most starred GitHub projects written mostly in TypeScript, excluding projects with more than 5000 tokens 
  and projects, containing only TypeScript header files. The resulting dataset contains 776 projects. Each project was 
  parsed with ```tsc``` compiler, which infers type information for each identifier (possibly ```any```).
  
### Comparison
  - Naive method, which assigns each identifier the type distribution it has at a training time.
  - Plain RNN &ndash; RNN without consistency layers, shows almost the same results
    - Has problems with consistency &ndash; it is possible that plain RNN suggests different types for different 
    occurrences of one identifier.
  - tsc+CheckJS &ndash; comparison against types that ```tsc``` could infer when also equipped with static 
  type-inference tool CheckJS. Hybrid model, which applies DeepTyper to the results of type inference conducted by 
  tsc+CheckJS. DeepTyper shows significant accuracy boost compared to CheckJS and hybrid model.
  - JSNice &ndash; tool for type-inference in JS functions using statistics from dependency graphs learned on a large 
  corpus. Since it is only available to use via a web form, only 30 experiments were held (30 random functions were 
  taken from the top-100 most starred JS projects on GitHub, for each function correct types were manually determined). 
  In the same manner with CheckJS hybrid model was invented. With confidence threshold set at 90% accuracy reaches 65%, 
  whereas there are only 2% incorrect or partially correct results, (for 167 identifiers, which needed type-inference). 
  It is 10% more, compared to JSNice and DeepTyper with 0% threshold, whereas it shows less incorrect results.
  

### Main pros of DeepTyper
  - Consistency
  - Indifference to structural information
    - Both pro and flaw &ndash; DeepTyper can be applied to many languages, but it does not take in account properties 
    of a specific language.


## TypeWriter

Article link: https://arxiv.org/pdf/1912.03768.pdf  

### Work and training time
  - Require investigation

### Gist 
Several RNN's for tokens, identifiers and comments, modified by feedback-directed search, and a static type checker. 
Top-k types inferred by model are passed to a tool searching for consistent types. That is done in the following manner:  
  1. Assume most predictions by TypeWriter are correct
  2. Try to set all unknown (not annotated types) to top-1 prediction by TypeWriter
  3. Run static type-checker on the new code
  4. Try to minimize feedback function (weighted sum of yet unpredicted annotations and type errors caused by incorrect 
  predictions) by changing some predictions to other candidates
  5. End or go to 3.
  
Model predicts function arguments types and function return type separately.  

To annotate function arguments it uses such features as:  
  - function name
  - argument name
  - all other arguments names
  - all usages of argument
  - argument type  
  
To annotate function return type it uses such features as:
  - function name
  - sequence of argument names
  - comment assosiated with function
  - set of return statements
  - return type

### Noticed problems  
 - Closed-world type suggestion
 - Feedback-directed search is slow
 - Structural information is not considered (except some, like usage sentences and return statements)

### Data  
  - Internal Facebook code base
  - `python3` tagged GitHub projects with more than 50 stars, all Python project with `mypy` as dependency on 
  Library.io, in total 1137 repos and approx. 12000 files, where 55% of functions have annotated return types and 41% 
  of arguments have type annotations  

### Comparison
  - Naive model &ndash; considers top-10 most frequent types in dataset and samples its prediction from the distribution 
  of these 10 types
  - DeepTyper &ndash; reimplementation of DeepTyper project for Python
  - NL2Type &ndash; reimplementation of NL2Type project for Python  

TypeWriter outperforms all three presented models both in arguments type prediction task and return type prediction task 
by approx. 10-15% for top-1 precision, and shows somewhat similar result to DeepTyper and NL2Type for top-3 and top-5 
precision. The same can be applied to recall.

  - pyre infer &ndash; static type inference tool. Comparison was conducted on a set of randomly chosen, fully annotated 
  files from the industrial codebase at Facebook. TypeWriter outperforms pyre infer, suggesting much more types than 
  static analysis. In most cases where both tools suggest a type, they suggest identical type.

### Pros of TypeWriter  
  - Code passed through TypeWriter does not contain type errors if it did not contain any
  - TypeWriter considers tasks of predicting function return type and function arguments type as different tasks, thus 
  solving each task more precisely
  - TypeWriter captures some structural information about source code, like usage statements and return statements, 
  unlike DeepTyper

## Typilus

Article link: https://arxiv.org/pdf/2004.10657.pdf  

### Work and training time  
  - single training epoch takes 86sec on a Nvidia K80 GPU
  - single inference epoch takes 7.3sec

### Gist
GNN with several types of edges adapted to Python syntax and nodes that uses deep similarity learning and KNN to tackle 
the open-world type suggestion problem, also uses type checker to deal with false positive guesses.


### Noticed problems  
 - Yet to investigate
 
### Data 
  - 600 Python Github repositories that contain at least one type annotation
  - `pytype` is run to augment corpus with type annotations that can be inferred from a static analysis tool
  - Deduplication tool is run, removing code duplicates to exclude bias from results
  - Around 25k non-Any types, most popular are `str`, `int` and `bool`
  - Top-10 types account for more than half of the dataset. However, types, used less than 100 times, still account 
  for 32% of the dataset.

### Comparison  
  - DeepTyper-based models &ndash; reimplementation of DeepTyper project for python, using subtokens instead of full 
  tokens and consistency module added to output biGRU layer
  - code2seq-based models

In all experiments all the models except Typilus were trained with different way of training. There are 3 options:
 - The classification-based loss that narrows the problem to closed-world type suggestion
 - Deep similarity learning that produces a type space
 - `Typilus loss` that combines both  

Typilus outperforms all presented models, reaching 54.6% accuracy when considering exact type match with 77.2% accuracy 
on common types (types used at least 100 times in the dataset) and 22.5% accuracy on rare types (all other types). When 
considering match up to parametric type (match when ignoring type parameters) it achieves 64.1% accuracy, with 80.3% 
accuracy on common types and 41.2% accuracy on rare types.

### Pros of Typilus:  
  - Open-world type suggestion
  - Type checker to deal with false positives guesses
  - GNN to capture structural data

## LambdaNet

Article link: https://openreview.net/pdf?id=Hkx6hANtwH  

### Gist
GNN with several types of hyperedges, adapted to TypeScript syntax.  

### Noticed problems 
 - No comparison with tools supporting open-world type suggestion
 - No type-checking after predicting (no guarantee that assigned type is consistent)
 - Collapsing generics into base type

### Data 
  - 300 popular TypeScript projects from Github that contain between 500 and 10000 lines of code and where at least 10% 
  of type annotations are user-defined types
  - Only 2.7% of the code is duplicated, so deduplication is not run
  - Infer types with TypeScript compiler 

### Comparison 
  - DeepTyper &ndash; LambdaNet outperforms DeepTyper by 14% considering variable declaration (since DeepTyper predicts 
  types for all occurrences) when DeepTyper splits words into subtokens
  - JSNice &ndash; comparing on top-level functions randomly selected from the test set, leaving functions containing 
  only library types, thus leaving only 107 function parameters and return types. LambdaNet correctly predicted 77 of 
  them, whereas JSNice only predicted 48
 
### Pros of LambdaNet:
  - GNN &ndash; captures structural information
  - Open-world type suggestion
